package stream

import config.StreamConfig
import fs2.kafka.{AutoOffsetReset, CommittableConsumerRecord, ConsumerSettings, KafkaConsumer}
import cats.effect.{Async, ContextShift, ExitCode, IO, Sync, Timer}
import cats._
import cats.syntax._
import cats.implicits._
import domain.{Anomaly, AnomalyMessage, KeyedAnomalyMessage, ScalingCandidate, TargetAndDeployment, TargetDependency}
import fs2.Stream
import io.circe
import org.slf4j.{Logger, LoggerFactory}
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import kube.KubernetesAPI
import servicegraph.ServiceDependencyGraph
import skuber.apps.v1.Deployment

class PrometheusAnomalyStream(
  serviceGraph: ServiceDependencyGraph,
  streamConfig: StreamConfig,
  kubernetesAPI: KubernetesAPI,
  serviceMaxReplicaMap: Map[String, Int],
  serviceMinReplicaMap: Map[String, Int]
)(implicit cs: ContextShift[IO], timer: Timer[IO], sync: Sync[IO], async: Async[IO])
    extends AnomalyStream {
  private val log: Logger =
    LoggerFactory.getLogger(getClass.getSimpleName)

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Latest)
      .withBootstrapServers(streamConfig.bootstrapServer)
      .withGroupId(streamConfig.consumerGroup)

  /**
   * Runs the stream
   * @return
   */
  override def runForever(): IO[ExitCode] =
    anomalyStream().compile.drain.as(ExitCode.Success)

  /**
   * Definition of the stream with parallelism and so forth enabled
   * @return
   */
  private[stream] def anomalyStream(): Stream[IO, Unit] =
    KafkaConsumer
      .stream(consumerSettings)
      .evalTap(_.subscribeTo(streamConfig.topic))
      .flatMap(_.stream)
      .mapAsync(streamConfig.streamParallelismMax)(processKafkaRecord)
      .unNone
      .mapAsync(streamConfig.streamParallelismMax)(createAnomaly)
      .mapAsync(streamConfig.streamParallelismMax)(createScalingCandidates)
      .flatMap(Stream.emits(_))
      .mapAsync(streamConfig.streamParallelismMax)(getCurrentDeployment)
      .unNone
      .mapAsync(streamConfig.streamParallelismMax)(scaleServiceAndDependencies)

  /**
   * Uses an Anomaly to obtain the different services that will need to be scaled in/out
   * Includes dependent services
   * @param anomaly
   * @return
   */
  private[stream] def createScalingCandidates(anomaly: Anomaly): IO[Seq[ScalingCandidate]] = IO {
    val scalingFunction = anomaly.function
    val anomalyScalingCandidateSingleton = Seq(
      ScalingCandidate(anomaly.target, anomaly.maxReplicas, anomaly.minReplicas, scalingFunction)
    )

    val dependencyScalingCandidates = anomaly.dependencies.map { dep =>
      ScalingCandidate(dep.target, dep.maxReplicas, dep.minReplicas, scalingFunction)
    }
    if(dependencyScalingCandidates.nonEmpty)
      log.info(s"Will scale [${dependencyScalingCandidates.map(_.serviceName).mkString(",")}] ${scalingFunction} as they are dependencies of ${anomaly.target}")
    anomalyScalingCandidateSingleton |+| dependencyScalingCandidates //Join the original anomaly-induced service and dependencies
  }

  /**
   * Scale both the original anomaly-induced service and services that it depends on
   * @param targetAndDeployment
   * @return
   */
  private[stream] def scaleServiceAndDependencies(
    targetAndDeployment: TargetAndDeployment
  ): IO[Unit] = {
    //TODO: Check for invalid scale function here
    val currentDeploymentReplicas = targetAndDeployment.deployment.spec.get.replicas.get //Obtain nReplicas from Skuber

    val scaleIO =
      if (targetAndDeployment.target.function.equals("out")) {
        if (currentDeploymentReplicas < targetAndDeployment.target.maxReplicas)
          IO(
            log.info(s"Scaling ${targetAndDeployment.target.serviceName} ${targetAndDeployment.target.function}")
          ) >> kubernetesAPI.scaleUp(
            targetAndDeployment.deployment,
            currentDeploymentReplicas
          )
        else
          IO(
            log.warn(
              s"Cannot scale ${targetAndDeployment.target.serviceName} ${targetAndDeployment.target.function} as maximum replicas have been reached"
            )
          )
      } else { //Path of execution for scaling in
        if (targetAndDeployment.target.minReplicas < currentDeploymentReplicas)
          IO(
            log.info(s"Scaling ${targetAndDeployment.target.serviceName} ${targetAndDeployment.target.function}")
          ) >> kubernetesAPI.scaleDown(
            targetAndDeployment.deployment,
            currentDeploymentReplicas
          )
        else
          IO(
            log.warn(
              s"Cannot scale ${targetAndDeployment.target.serviceName} ${targetAndDeployment.target.function} as minimum replicas have been reached"
            )
          )
      }

    //Return the function encapsulated in IO
    scaleIO
  }

  /**
   * Retrieve a deployment from Kubernetes using Skuber
   * @param target
   * @return
   */
  private[stream] def getCurrentDeployment(
    target: ScalingCandidate
  ): IO[Option[TargetAndDeployment]] =
    kubernetesAPI
      .getDeploymentByName(target.serviceName)
      .flatMap { deploymentOpt =>
        IO(deploymentOpt.map(deployment => TargetAndDeployment(target, deployment)))
      }// Execute an IO and retrieve an Option of deployment, then create a case class to represent the value

  /**
   * Takes a Kafka Record, validates it then returns the required content from it. Decodes from JSON
   * @param committableRecord
   * @return
   */
  private[stream] def processKafkaRecord(
    committableRecord: CommittableConsumerRecord[IO, String, String]
  ): IO[Option[KeyedAnomalyMessage]] =
    decode[AnomalyMessage](committableRecord.record.value) match {
      case Right(value) =>
        IO(
          Option(KeyedAnomalyMessage(committableRecord.record.key, value))
        )
      case Left(_) =>
        IO(
          log.error(s"Error decoding record with key ${committableRecord.record.key} from topic ${streamConfig.topic}")
        ) >> IO.pure(None)
    }

  private[stream] def getMinAndMaxReplicas(serviceName: String): (Int, Int) = {
    //Gracefully extract the number of replicas from an Option
    val maxReplicas = serviceMaxReplicaMap.get(serviceName) match {
      case Some(value) => value
      case None =>
        log.error(
          s"Could not retrieve maximum replicas for ${serviceName}. Defaulting to 1"
        )
        1
    }
    //Gracefully extract the number of replicas from an Option
    val minReplicas = serviceMinReplicaMap.get(serviceName) match {
      case Some(value) => value
      case None =>
        log.error(
          s"Could not retrieve minimum replicas for ${serviceName}. Defaulting to 1"
        )
        1
    }

    (minReplicas, maxReplicas) //Return a tuple of min and max replicas for the given service
  }

  /**
   * Takes a Kafka message and converts it to a case class that contains the data needed for scaling
   * @param keyedAnomalyMessage
   * @return
   */
  private[stream] def createAnomaly(
    keyedAnomalyMessage: KeyedAnomalyMessage
  ): IO[Anomaly] = IO {
    val (minReplicas, maxReplicas) = getMinAndMaxReplicas(keyedAnomalyMessage.message.targetAppName)

    Anomaly(
      keyedAnomalyMessage.message.targetAppName,
      serviceGraph.inferTargets(keyedAnomalyMessage.message.targetAppName),
      maxReplicas,
      minReplicas,
      keyedAnomalyMessage.message.function //TODO: Add validation here, should be OK from topic but best to check
    )
  }

  private[stream] def getReplicasFromDeployment(deployment: Deployment): IO[Int] =
    IO {
      deployment.spec.get.replicas.getOrElse {
        log.error(s"Could not retrieve number of replicas for deployment ${deployment.name}. Defaulting to 1")
        1
      }
    }
}

object PrometheusAnomalyStream {
  def apply(
    serviceGraph: ServiceDependencyGraph,
    streamConfig: StreamConfig,
    kubernetesAPI: KubernetesAPI,
    serviceMaxReplicaMap: Map[String, Int],
    serviceMinReplicaMap: Map[String, Int]
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): PrometheusAnomalyStream =
    new PrometheusAnomalyStream(serviceGraph, streamConfig, kubernetesAPI, serviceMaxReplicaMap, serviceMinReplicaMap)
}
