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

  val buildVersion = Option(System.getenv("GO_PIPELINE_LABEL")).getOrElse("LOCAL")
  val debugging: Boolean = ManagementFactory.getRuntimeMXBean().getInputArguments().exists(_ == "-Xdebug")

  lazy val buildSettings = {
    
    val zipDependencies = Seq(
      packageBin in Compile
    )    
    
    val testZipDependencies = Seq(      
      // packageBin in Compile,
      // Keys.`package` in Shared//,
      // packageBin in IntegrationTests,
      // packageBin in FunctionalTests,
      // packageBin in SystemTests
    )    
    
    def zipTaskSettings(key:TaskKey[Unit], task:Initialize[sbt.Task[Unit]], dependencies: Seq[ScopedTask[_]], artifactSuffix: String = "") = {
      (key <<= task) ++
        (artifact in key <<= name(n => Artifact(n + artifactSuffix, "zip", "zip"))) ++
        (artifactPath in key <<= artifactPathSetting(artifact in key)) ++
        (key <<= key.dependsOn(packageBin in Shared))
        // dependencies.map {(key <<= (key).dependsOn(_))}
    }
    
    // --- zip task
    lazy val zip = TaskKey[Unit]("zip", "Creates a deployable artifact.")
    def zipTask = {
      (fullClasspath in Runtime, artifactPath in zip, artifactPath in (Compile, Keys.packageBin), streams) map {
        (classpath, artifactPath, coreJarPath, streams) =>
          val libs = (classpath.files +++ coreJarPath).get x flatRebase("lib")

          sbt.IO.zip(libs, artifactPath)
          streams.log.info("Created Zip:" + artifactPath)
      }
    }
    
    // --- tests-zip task
    lazy val testsZip = TaskKey[Unit]("tests-zip", "Creates an artifact which contains integration and functional tests.")
    def testsZipTask = {
      (fullClasspath in Shared, artifactPath in testsZip, packageBin in Shared, packageBin in IntegrationTests, packageBin in FunctionalTests, packageBin in SystemTests, streams) map {
        (classpath, artifactPath, sharedJarPath, integrationJarPath, functionalJarPath, systemJarPath, streams) =>
          val libs = (classpath.files +++ sharedJarPath +++ integrationJarPath +++ functionalJarPath +++ systemJarPath).get x flatRebase("lib")

          sbt.IO.zip(libs, artifactPath)
          streams.log.info("Created Tests Zip:" + artifactPath)
      }
    }
    
        
    
    Seq(
      scalaVersion := "2.10.2",
      libraryDependencies := Seq("org.scalatest" % "scalatest_2.10" % "1.9.2" % "shared")      
    ) ++
    sharedSettings ++ unitTestSettings ++ integrationTestSettings ++ functionalTestSettings ++ systemTestSettings
    zipTaskSettings(zip, zipTask, zipDependencies) ++
    zipTaskSettings(testsZip, testsZipTask, testZipDependencies, "-tests")    
  }

  lazy val Shared = config("shared") extend(Runtime) // FIXME could we rename this to test?
  lazy val sharedSettings = createTestSettings("shared", Shared, Defaults.configSettings)

  lazy val UnitTests = config("unit") extend(Shared)
  lazy val unitTestSettings = createTestSettings("unit", UnitTests)

  lazy val IntegrationTests = config("integration") extend(Shared)
  lazy val integrationTestSettings = createTestSettings("integration", IntegrationTests)

  lazy val FunctionalTests = config("functional") extend(Shared)
  lazy val functionalTestSettings = createTestSettings("functional", FunctionalTests)
  
  lazy val SystemTests = config("system") extend(Shared)
  lazy val systemTestSettings = createTestSettings("system", SystemTests)  

  private def createTestSettings(testType: String, testConfiguration: Configuration, settings: Seq[Setting[_]] = Defaults.testSettings) = {
    inConfig(testConfiguration)(settings) ++
    ((fork in testConfiguration) := !debugging) ++    
    (sourceDirectory in testConfiguration <<= sourceDirectory in Test) ++
    (classDirectory in testConfiguration <<= classDirectory in Test) ++    
    (sources in testConfiguration ~= { _ filter { shouldInclude(_, testType) } } ) ++
    (testOptions in testConfiguration += Tests.Argument("-oDF")) ++
    (artifactName in testConfiguration := { (scalaversion, moduleId, artifact) => "%s_%s-%s-%s.jar" format (moduleId.name, scalaversion.binary, buildVersion, testType) } ) ++
    (mappings in (testConfiguration, Keys.packageBin) ~= { (ms: Seq[(File,String)]) => ms.filter { case (file, path) =>{ file.getName.endsWith(".class")==false || file.getAbsolutePath.contains(testType) } } })    
  }
    
  private def shouldInclude(testFile: File, testType: String) = {
    testFile.toURI.toString.contains("/" + testType + "/")
  }  
    
}

object CasperBuild extends Build {

  import BuildSettings._

  lazy val core = Project("core", file("."))  
    .configs(Shared)
    .configs(UnitTests)
    .configs(IntegrationTests)
    .configs(FunctionalTests)
    .configs(SystemTests)
    .settings(buildSettings : _*)
}

