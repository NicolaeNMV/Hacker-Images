package models

import play.api._
import play.api.libs.concurrent._

import org.jsoup.Jsoup
import org.jsoup.nodes._

import collection.JavaConversions._

trait ImageExtractor {
  def getImageUrl(String pageUrl): Promise[Option[String]]
}

object ScreenshotExtractor extends ImageExtractor {
  def getImageUrl(): Promise[[String]] = {
    Promise.pure(None)
  }
}

object MostRelevantPageImageExtractor extends ImageExtractor {
  def getImageUrl(): Promise[String] = {
    Promise.pure(None)
  }
}

