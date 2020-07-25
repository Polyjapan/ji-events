import sbt.Keys.{libraryDependencies, resolvers}

ThisBuild / organization := "ch.japanimpact"
ThisBuild / version      := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.1"
ThisBuild / libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "ch.japanimpact" %% "jiauthframework" % "1.0-SNAPSHOT",
  "com.typesafe.play" %% "play-json-joda" % "2.8.1",
)

lazy val api = (project in file("api/"))
  .settings(
    name := "ji-events-api",
    publishArtifact in(Compile, packageDoc) := false,
    publishArtifact in(Compile, packageSrc) := false,
    publishArtifact in(Compile, packageBin) := true,
    libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.8.1",
    libraryDependencies += "com.pauldijou" %% "jwt-play-json" % "4.2.0"
)


lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := "ji-events",
    libraryDependencies ++= Seq(jdbc, evolutions, ehcache, ws, specs2 % Test, guice),

    libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.6.4",
    libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.34",
    libraryDependencies += "com.pauldijou" %% "jwt-play" % "4.2.0",

    resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/",
    resolvers += Resolver.mavenCentral,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation"
    )
  )
  .aggregate(api)
  .dependsOn(api)

