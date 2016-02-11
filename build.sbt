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
    name := "releases-actorddd"
  ).
  dependsOn(RootProject(uri("git://github.com/DrewEaster/actorddd.git#library")))
//  dependsOn(RootProject(file("../actorddd")))

Revolver.settings
