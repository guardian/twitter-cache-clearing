import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._

name := "twitter-cache-clearing-by-specification"

version := "0.1"

scalaVersion := "2.13.1"

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ssm" % "1.11.505",
  "com.amazonaws" % "amazon-kinesis-client" % "1.9.3",
  "com.amazonaws" % "aws-lambda-java-events" % "2.2.7",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.gu" %% "thrift-serializer" % "4.0.2",
  "com.gu" %% "content-api-models-scala" % "15.5",
  "com.gu" %% "content-api-client-default" % "15.4",
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "com.softwaremill.sttp.client" %% "core" % "2.0.0-RC2",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.0.0-RC2",
  "com.danielasfregola" %% "twitter4s" % "6.2",
  "org.scalatest" %% "scalatest" % "3.1.0" % "test"
)

enablePlugins(RiffRaffArtifact)

riffRaffPackageType := assembly.value

riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
