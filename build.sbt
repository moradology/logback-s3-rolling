name          := "logback-s3-appender"
organization  := "geotrellis"
version       := "0.0.1"
scalaVersion  := "2.11.8"
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-Yinline-warnings",
  "-language:implicitConversions",
  "-language:reflectiveCalls",
  "-language:higherKinds",
  "-language:postfixOps",
  "-language:existentials",
  "-feature")

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7" % "provided",
  "com.amazonaws"  % "aws-java-sdk-s3" % "1.11.29"
)

parallelExecution in Test := false

lazy val root = project.in(file("."))

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
  case "META-INF/BCKEY.SF" => MergeStrategy.discard
  case "META-INF/BCKEY.DSA" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

