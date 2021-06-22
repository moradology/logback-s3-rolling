package ch.qos.logback.core.rolling

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.Date

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client

import ch.qos.logback.core.rolling.helper.CompressionMode._


/**
 * Based on https://github.com/shuwada/logback-s3
 *
 * On each rolling event (which is defined by <triggeringPolicy>), this policy does:
 * 1. Incremented log file rolling
 * 2. Uploading of the rolled log file to an S3 bucket
 *
 * This policy uploads the active log file on JVM exit.
 *
**/
class S3FixedWindowRollingPolicy extends FixedWindowRollingPolicy {

  val executor: ExecutorService = Executors.newFixedThreadPool(1)

  var awsAccessKey: String = null
  var awsSecretKey: String = null
  var s3BucketName: String = null
  var s3FolderName: String = null
  var lastRolled = 0

  var s3Client: AmazonS3Client = null

  def getS3Client(): AmazonS3Client = {
    if (s3Client == null) {
      val cred: AWSCredentials = new BasicAWSCredentials(getAwsAccessKey(), getAwsSecretKey())
      s3Client = new AmazonS3Client(cred)
    }
    s3Client
  }

  override def start: Unit = {
    super.start()
    // add a hook on JVM shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHookRunnable()))
  }

  override def rollover(): Unit = {
    // Inside this method it is guaranteed that the hereto active log file is
    // closed.
    val nextNameStr = fileNamePattern.convertInt(lastRolled + 1)

    // move active file name to min
    compressionMode match {
      case NONE =>
        util.rename(getActiveFileName(), nextNameStr)
      case GZ =>
        compressor.compress(getActiveFileName(), nextNameStr, null);
      case ZIP =>
        compressor.compress(getActiveFileName(), nextNameStr, zipEntryFileNamePattern.convert(new Date()));
    }

    lastRolled = lastRolled + 1

    // upload the current log file into S3
    val rolledLogFileName: String = fileNamePattern.convertInt(lastRolled)
    uploadFileToS3Async(rolledLogFileName)
  }

  def uploadFileToS3Async(filename: String): Unit = {
    val file = new File(filename)

    // if file does not exist or is empty, do nothing
    if (file.exists() && file.length() != 0) {
      // add the S3 folder name in front if specified
      val s3ObjectName = if (getS3FolderName() != null) {
        getS3FolderName + "/" + file.getName()
      } else {
        file.getName()
      }

      addInfo("Uploading " + filename)
      val uploader: Runnable = new Runnable() {
        override def run(): Unit = {
          try {
            getS3Client().putObject(getS3BucketName(), s3ObjectName, file)
          } catch { case ex: Exception =>
            ex.printStackTrace()
          }
        }
      }
      executor.execute(uploader)
    }
  }

  // On JVM exit, upload the current log
  class ShutdownHookRunnable extends Runnable {

    override def run(): Unit = {
      try {
        rollover()
        uploadFileToS3Async(fileNamePattern.convertInt(lastRolled))

        // wait until finishing the upload
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.MINUTES)
      } catch { case ex: Exception =>
        addError("Failed to upload a log in S3", ex)
        executor.shutdownNow()
      }
    }
  }


  def getAwsAccessKey(): String = awsAccessKey

  def setAwsAccessKey(accessKey: String): Unit = {
    this.awsAccessKey = accessKey
  }

  def getAwsSecretKey(): String = awsSecretKey

  def setAwsSecretKey(secretKey: String): Unit = {
    this.awsSecretKey = secretKey
  }

  def getS3BucketName(): String = s3BucketName

  def setS3BucketName(bucketName: String): Unit = {
    this.s3BucketName = bucketName
  }

  def getS3FolderName(): String = s3FolderName

  def setS3FolderName(folderName: String): Unit = {
    s3FolderName = folderName
  }
}
