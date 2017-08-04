addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.5.0-M1")

credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")

resolvers += Resolver.url("lightbend-commercial",
  url("https://repo.lightbend.com/commercial-releases"))(Resolver.ivyStylePatterns)