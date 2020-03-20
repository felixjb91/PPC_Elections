package upmc.akka.leader

import java.util
import java.util.Date

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

     var time : Int = 500
     val father = context.parent

     var nodesAlive:List[Int] = List()
     var nodesAliveOld:List[Int] = List()
     var datesForChecking:List[Date] = List()
     var lastDate:Date = null

     var leader : Int = 0

    def receive = {

         // Initialisation
        case Start => {
          self ! CheckerTick
        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive (nodeId) => {
//          father ! Message ("Node "+ nodeId + " is alive" )
          if (!nodesAlive.contains(nodeId)) {
            nodesAlive = nodesAlive:::List(nodeId)
          }
          if(!nodesAlive.contains(nodeId)) nodesAlive = nodesAlive:::List(nodeId)
        }

        case IsAliveLeader (nodeId) => {
//          if(nodeId == this.id) father ! Message ("I'm the Leader !")
//          else father ! Message ("Node "+ nodeId + " is leader and is alive" )
          if (leader != nodeId) {
            leader = nodeId
            father ! LeaderChanged (nodeId)
          }
          if(!nodesAlive.contains(nodeId)) nodesAlive = nodesAlive:::List(nodeId)
        }


        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort
        case CheckerTick => {
          context.system.scheduler.scheduleOnce(this.time milliseconds, self, CheckerTick)

          for(i <- nodesAliveOld.indices) {
            val current = nodesAliveOld(i)
            if (!nodesAlive.contains(current)) {
              if (current == leader) {
                father ! Message ("Node " + current + " was leader but is dead !!!!!!!!!!!!!!!!!")
              }
              else father ! Message ("Node " + current + " is dead !")
            }
          }
          nodesAliveOld = nodesAlive
          nodesAlive = List()
        }

    }


}
