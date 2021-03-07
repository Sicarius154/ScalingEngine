package stream

import config.StreamConfig
import fs2.kafka.{
  ConsumerSettings,
  AutoOffsetReset,
  CommittableConsumerRecord,
  KafkaConsumer
}
import cats.effect.{ContextShift, Async, Timer, ExitCode, IO, Sync}
import cats.syntax._
import cats.implicits._
import domain.{
  ScalingTarget,
  TargetWithDependencies,
  AnomalyMessage,
  KeyedAnomalyMessage
}
import fs2.Stream
import io.circe
import org.slf4j.{Logger, LoggerFactory}
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import kube.KubernetesAPI
import servicegraph.ServiceDependencyGraph
import skuber.apps.v1.Deployment
import scala.concurrent.duration._

class PrometheusAnomalyStream(
    serviceGraph: ServiceDependencyGraph,
    streamConfig: StreamConfig,
    kubernetesAPI: KubernetesAPI
)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO],
    sync: Sync[IO],
    async: Async[IO]
) extends AnomalyStream {
  private val log: Logger =
    LoggerFactory.getLogger(getClass.getSimpleName)

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Latest)
      .withBootstrapServers(streamConfig.bootstrapServer)
      .withGroupId(streamConfig.consumerGroup)

  override def runForever(): IO[ExitCode] =
    anomalyStream().compile.drain.as(ExitCode.Success)

  private[stream] def anomalyStream(): Stream[IO, Unit] =
    KafkaConsumer
      .stream(consumerSettings)
      .evalTap(_.subscribeTo(streamConfig.topic))
      .flatMap(_.stream)
      .mapAsync(streamConfig.streamParallelismMax)(processKafkaRecord)
      .mapAsync(streamConfig.streamParallelismMax)(inferDependencies)
      .mapAsync(streamConfig.streamParallelismMax)(getCurrentDeployment)
      .mapAsync(streamConfig.streamParallelismMax)(scale)

  private[stream] def scale(deploymentOpt: Option[Deployment]): IO[Unit] =
    deploymentOpt match {
      case Some(deployment) =>
        IO(log.info(s"Scaling ${deployment.name} up")) >> kubernetesAPI.scaleUp(
          deployment,
          deployment.spec.get.replicas.get
        )
      case None => IO.unit
    }

  private[stream] def getCurrentDeployment(
      targetOpt: Option[TargetWithDependencies]
  ): IO[Option[Deployment]] =
    targetOpt match {
      case Some(target) => kubernetesAPI.getDeploymentByName(target.target)
      case None         => IO.pure(None)
    }

  private[stream] def processKafkaRecord(
      committableRecord: CommittableConsumerRecord[IO, String, String]
  ): IO[Option[KeyedAnomalyMessage]] = {
    decode[AnomalyMessage](committableRecord.record.value) match {
      case Right(value) =>
        IO.pure(
          Option(KeyedAnomalyMessage(committableRecord.record.key, value))
        )
      case Left(_) =>
        IO {
          log.error(
            s"Error decoding record with key ${committableRecord.record.key} from topic ${streamConfig.topic}"
          )
          None
        }
    }
  }

  private[stream] def inferDependencies(
      keyedAnomalyMessageOpt: Option[KeyedAnomalyMessage]
  ): IO[Option[TargetWithDependencies]] =
    IO {
      keyedAnomalyMessageOpt
        .map { keyedAnomalyMessage =>
          TargetWithDependencies(
            keyedAnomalyMessage.message.targetAppName,
            serviceGraph.inferTargets(keyedAnomalyMessage.message.targetAppName)
          )
        }
    }
}

object PrometheusAnomalyStream {
  def apply(
      serviceGraph: ServiceDependencyGraph,
      streamConfig: StreamConfig,
      kubernetesAPI: KubernetesAPI
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): PrometheusAnomalyStream =
    new PrometheusAnomalyStream(serviceGraph, streamConfig, kubernetesAPI)
}
