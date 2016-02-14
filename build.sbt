lazy val commonSettings = Seq(
  organization  := "com.example",
  version       := "0.1",
  scalaVersion  := "2.11.7",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
)

lazy val `releases-actorddd` = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "releases-actorddd",
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    libraryDependencies ++= Seq(
      "pl.newicom.dddd" %% "akka-ddd-core" % "1.1.0-SNAPSHOT",
      "pl.newicom.dddd" %% "akka-ddd-messaging" % "1.1.0-SNAPSHOT",
      "pl.newicom.dddd" %% "akka-ddd-monitoring" % "1.1.0-SNAPSHOT",
      "pl.newicom.dddd" %% "view-update-sql" % "1.1.0-SNAPSHOT",
      "pl.newicom.dddd" %% "eventstore-akka-persistence" % "1.1.0-SNAPSHOT",
      "com.github.tminglei" %% "slick-pg" % "0.10.0" exclude("org.slf4j", "slf4j-simple")
    )
  ).
  dependsOn(RootProject(uri("git://github.com/DrewEaster/actorddd.git#library")))
//  dependsOn(RootProject(file("../actorddd")))
