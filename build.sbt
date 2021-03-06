name := "ScalingEngine"

version := "0.1"

scalaVersion := "2.13.4"

resolvers ++= Seq(
  Classpaths.typesafeReleases,
  "confluent" at "https://packages.confluent.io/maven/",
  Resolver.mavenLocal
)

fork := true

javaOptions := Seq("-Dskuber.log.request=false;-Dskuber.log.response=false")

lazy val root = (project in file("."))
  .settings(
    organization := "com.chris",
    name := "ScalingEngine",
    version := "0.0.1-SNAPSHOT",
    libraryDependencies ++= Seq(
      Dependencies.Circe.circeGeneric,
      Dependencies.Cats.catsCore,
      Dependencies.Cats.catsEffect,
      Dependencies.Config.pureConfig,
      Dependencies.Logging.logbackClassic,
      Dependencies.Circe.circeParser,
      Dependencies.Testing.scalaTest,
      Dependencies.Http4s.http4sBlaze,
      Dependencies.Http4s.http4sBlazeClient,
      Dependencies.Http4s.http4sDsl,
      Dependencies.Fs2.fs2Core,
      Dependencies.Fs2.fs2Kafka,
      Dependencies.TestContainers.testContainersScalaTest,
      Dependencies.TestContainers.testContainerKafka,
      Dependencies.Skuber.skuber
    )
  )

assemblyMergeStrategy in assembly := {
  case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "StaticLoggerBinder.class" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "StaticMDCBinder.class" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "StaticMarkerBinder.class" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "io.netty.versions.properties" =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "BUILD" => MergeStrategy.first
  case "application.conf"                            => MergeStrategy.concat
  case "unwanted.txt"                                => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
