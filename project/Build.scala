import sbt._
import sbt.Configuration
import sbt.Keys._
import scala._
import scala.Predef._

object BuildSettings {
  import Project._
  import Defaults._

  lazy val buildSettings = {
    Seq(
      scalaVersion := "2.10.2",
      libraryDependencies := Seq("org.scalatest" % "scalatest_2.10" % "1.9.2" % "unit, integration, functional")      
    ) ++ unitTestSettings ++ integrationTestSettings ++ functionalTestSettings
  }

  lazy val UnitTests = config("unit") extend(Runtime)
  lazy val unitTestSettings = createTestSettings("unit", UnitTests)

  lazy val IntegrationTests = config("integration") extend(Runtime)
  lazy val integrationTestSettings = createTestSettings("integration", IntegrationTests)

  lazy val FunctionalTests = config("functional") extend(Runtime)
  lazy val functionalTestSettings = createTestSettings("functional", FunctionalTests)

  private def createTestSettings(testType: String, testConfiguration: Configuration) = {
    inConfig(testConfiguration)(Defaults.testSettings) ++
    (sourceDirectory in testConfiguration <<= sourceDirectory in Test) ++
    (classDirectory in testConfiguration <<= classDirectory in Test) ++    
    (sources in testConfiguration ~= { _ filter { shouldInclude(_, testType) } } ) ++
    (testOptions in testConfiguration += Tests.Argument("-oDF"))
  }
    
  private def shouldInclude(testFile: File, testType: String) = {
    val path = testFile.toURI.toString
    path.contains("/" + testType + "/") || path.contains("/shared/")
  }  
}

object CasperBuild extends Build {

  import BuildSettings._

  lazy val core = Project("core", file("."))  
    .configs(UnitTests)
    .configs(IntegrationTests)
    .configs(FunctionalTests)        
    .settings(buildSettings : _*)
}

