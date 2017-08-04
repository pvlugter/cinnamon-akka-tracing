lazy val cinnamonAkkaTracing = project
  .in(file("."))
  .enablePlugins(Cinnamon)
  .settings(
    scalaVersion := "2.12.3",
    libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.5.3",
    libraryDependencies += Cinnamon.library.cinnamonOpenTracingZipkin,
    cinnamon in run := true,
    connectInput in run := true
  )
