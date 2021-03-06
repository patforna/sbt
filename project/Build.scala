import _root_.sbt.IO
import java.lang.management.ManagementFactory
import java.lang.System._
import java.net.URL
import sbt._
import sbt.Configuration
import sbt.Keys._
import scala._
import scala.collection.JavaConversions._

object BuildSettings {
  import Project._
  import Defaults._

  lazy val buildSettings = {
    Seq(
      scalaVersion := "2.10.2",
      libraryDependencies := Seq(
        "org.scalatest" % "scalatest_2.10" % "2.0"% "test, shared, unit, integration, functional",
        "org.scala-lang" % "scala-library" % "2.10.2" // why do we need this here?        
      )
    ) ++ 
    testSettings ++
    zipSettings
  }

  lazy val Shared = config("shared") extend(Runtime)
  lazy val UnitTests = config("unit") extend(Shared)  
  lazy val IntegrationTests = config("integration") extend(Shared)
  lazy val FunctionalTests = config("functional") extend(Shared)
  lazy val SystemTests = config("system") extend(Shared)

  lazy val testSettings = configure(Shared, Defaults.configSettings) ++ configure(UnitTests) ++ configure(IntegrationTests) ++ configure(FunctionalTests) ++ configure(SystemTests) ++ (testOptions += Tests.Argument("-oDF"))

  private def configure(config: Configuration, settings: Seq[Setting[_]] = Defaults.testSettings) = {
    inConfig(config)(settings) ++
    (sourceDirectory in config <<= sourceDirectory in Test) ++
    (classDirectory in config <<= classDirectory in Test) ++    
    (sources in config ~= { _ filter { shouldInclude(_, config.name) } } ) ++
    (mappings in (config, Keys.packageBin) ~= { _.filter { case (file, path) => { file.getAbsolutePath.contains(config.name) || !file.getName.endsWith(".class") } } } )    
  }
    
  private def shouldInclude(file: File, testType: String) = {
    file.toURI.toString.contains("/" + testType + "/")
  }  
  
  
  // --- zip task
  lazy val zipSettings = (zip <<= zipTask) ++ (zipTests <<= zipTestsTask)
  
  lazy val zip = TaskKey[Unit]("zip", "Creates a deployable artifact.")
  lazy val zipTests = TaskKey[Unit]("zip-tests", "Creates an artifact which contains integration, functional and system tests.")  
  
  def zipTask = {
    (fullClasspath in Runtime, Keys.packageBin in Compile, streams) map {
      (classpath, coreJar, streams) =>
        val libs = (classpath.files +++ coreJar).get x flatRebase("lib")

        val zipPath = coreJar.getAbsolutePath.replace(".jar", ".zip")
        sbt.IO.zip(libs, file(zipPath))
        streams.log.info("Created zip: " + zipPath)
    }
  }
  
  def zipTestsTask = {
    (fullClasspath in Runtime, fullClasspath in Shared, fullClasspath in IntegrationTests, fullClasspath in FunctionalTests, fullClasspath in SystemTests, Keys.packageBin in Compile, Keys.packageBin in Shared, Keys.packageBin in IntegrationTests, Keys.packageBin in FunctionalTests, Keys.packageBin in SystemTests, Keys.packageBin in (CasperBuild.platform, Compile), Keys.packageBin in (CasperBuild.platform, Shared), streams) map {
      (runtimeDeps, sharedDeps, integrationDeps, functionalDeps, systemDeps, coreJar, sharedJar, integrationJar, functionalJar, systemJar, platformJar, platformSharedJar, streams) =>
        val libs = (runtimeDeps.files +++ sharedDeps.files +++ integrationDeps.files +++ functionalDeps.files +++ systemDeps.files +++ coreJar +++ sharedJar +++ integrationJar +++ functionalJar +++ systemJar +++ platformJar +++ platformSharedJar).get x flatRebase("lib")

        val zipPath = coreJar.getAbsolutePath.replace(".jar", "-tests.zip")
        sbt.IO.zip(libs, file(zipPath))
        streams.log.info("Created zip: " + zipPath)
    }
  }  
}

object CasperBuild extends Build {

  import BuildSettings._
  
  lazy val platform = ProjectRef(file("platform"), "platform")  

  lazy val core = Project("core", file("."))  
    .configs(Shared, UnitTests, IntegrationTests, FunctionalTests, SystemTests)
    .settings(buildSettings : _*)
    .aggregate(platform)
    .dependsOn(platform % "compile;shared->shared")
    
}
