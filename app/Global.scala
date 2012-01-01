import play.api._
import play.api.Play.current
import akka.actor.Actor
import akka.actor.Actor._
import akka.actor.Scheduler
import java.util.concurrent.TimeUnit

import controllers._
import models._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    val linksFetchScheduler = actorOf[LinksFetchScheduler].start()
    Scheduler.schedule(linksFetchScheduler, "fetch", 1, 10, TimeUnit.SECONDS)
  }
}

class LinksFetchScheduler extends Actor {
  var i = 0
  def getNextLinksFetch() = {
    val source = Sources.staticSourcesList(i)
    i = if(i+1 < Sources.staticSourcesList.length) i+1 else 0
    source
  }
  val linksFetch = actorOf[LinksFetch].start()

  def receive = {
    case "fetch" => {
      linksFetch ! getNextLinksFetch()
    }
  }
}

class LinksFetch extends Actor {
  def receive = {
    case source:NewsSource => 
      Logger.debug("LinksFetch "+source)
      LinksFetcher.fetch(source.linksExtractor).map(_.map(link => ImageFetcher.fetch(link.url)(source.imageExtractor)))
  }
}
