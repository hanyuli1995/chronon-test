package ai.chronon.spark.test
import ai.chronon.aggregator.test.Column
import ai.chronon.api._
import ai.chronon.spark.Extensions._
import ai.chronon.spark.SparkSessionBuilder
import org.apache.spark.sql.SparkSession
import org.junit.Test
import ai.chronon.spark.stats.{StatsCompute, StatsGenerator}
import org.apache.spark.sql.functions.lit

class StatsComputeTest {
  lazy val spark: SparkSession = SparkSessionBuilder.build("StatsComputeTest", local = true)

  @Test
  def summaryTest(): Unit = {
    val data = Seq(
      ("1", Some(1L), Some(1.0), Some("a")),
      ("1", Some(1L), None, Some("b")),
      ("2", Some(2L), None, None),
      ("3", None, None, Some("d"))
    )
    val columns = Seq("keyId", "value", "double_value", "string_value")
    val rdd = spark.sparkContext.parallelize(data)
    val df = spark.createDataFrame(rdd).toDF(columns: _*).withColumn(Constants.PartitionColumn, lit("2022-04-09"))
    val stats = new StatsCompute(df, Seq("keyId"))
    val aggregator = StatsGenerator.buildAggregator(stats.metrics, stats.selectedDf)
    val result = stats.dailySummary(aggregator).toFlatDf
    stats.addDerivedMetrics(result, aggregator).show()
  }

  @Test
  def generatedSummaryTest(): Unit = {
    val schema = List(
      Column("user", StringType, 10),
      Column("session_length", IntType, 10000)
    )
    val df = DataFrameGen.events(spark, schema, 100000, 10)
    val stats = new StatsCompute(df, Seq("user"))
    val aggregator = StatsGenerator.buildAggregator(stats.metrics, stats.selectedDf)
    val daily = stats.dailySummary(aggregator, timeBucketMinutes = 0).toFlatDf

    println("Daily Stats")
    daily.show()
    val bucketed = stats.dailySummary(aggregator).toFlatDf
      .replaceWithReadableTime(Seq(Constants.TimeColumn), false)

    println("Bucketed Stats")
    bucketed.show()

    val denormalized = stats.addDerivedMetrics(bucketed, aggregator)
    println("With Derived Data")
    denormalized.show(truncate = false)
  }

  @Test
  def generatedSummaryNoTsTest(): Unit = {
    val schema = List(
      Column("user", StringType, 10),
      Column("session_length", IntType, 10000)
    )
    val df = DataFrameGen.events(spark, schema, 100000, 10)
      .drop(Constants.TimeColumn)
    val stats = new StatsCompute(df, Seq("user"))
    val aggregator = StatsGenerator.buildAggregator(stats.metrics, stats.selectedDf)
    val daily = stats.dailySummary(aggregator, timeBucketMinutes = 0).toFlatDf

    println("Daily Stats")
    daily.show()
    val bucketed = stats.dailySummary(aggregator).toFlatDf

    println("Bucketed Stats")
    bucketed.show()

    val denormalized = stats.addDerivedMetrics(bucketed, aggregator)
    println("With Derived Data")
    denormalized.show(truncate = false)
  }

  /**
    * Test to make sure aggregations are generated when it makes sense.
    * Example, percentiles are not currently supported for byte.
    */
  @Test
  def generatedSummaryByteTest(): Unit = {
    val schema = List(
      Column("user", StringType, 10),
      Column("session_length", IntType, 10000)
    )
    val byteSample = 1.toByte
    val df = DataFrameGen.events(spark, schema, 100000, 10)
      .withColumn("byte_column", lit(byteSample))
    val stats = new StatsCompute(df, Seq("user"))
    val aggregator = StatsGenerator.buildAggregator(stats.metrics, stats.selectedDf)
    val daily = stats.dailySummary(aggregator, timeBucketMinutes = 0).toFlatDf

    println("Daily Stats")
    daily.show()
    val bucketed = stats.dailySummary(aggregator).toFlatDf
      .replaceWithReadableTime(Seq(Constants.TimeColumn), false)

    println("Bucketed Stats")
    bucketed.show()

    val denormalized = stats.addDerivedMetrics(bucketed, aggregator)
    println("With Derived Data")
    denormalized.show(truncate = false)
  }
}
