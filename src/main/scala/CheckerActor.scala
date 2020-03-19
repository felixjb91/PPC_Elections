package upmc.akka.leader

import java.util
import java.util.Date

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

     var time : Int = 200
     val father = context.parent

     var nodesAlive:List[Int] = List()
     var datesForChecking:List[Date] = List()
     var lastDate:Date = null

     var nodesChecked: List[Int] = List()

     var leader : Int = 0

    def receive = {

         // Initialisation
        case Start () => {
            self ! CheckerTick
            father ! Message("test!!!")
        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive (nodeId) => {
          if (!nodesAlive.contains(nodeId)) {
            nodesAlive:::List(nodeId)
            father ! Message ("Node " + nodeId + " is born !")
          }

        }
//          father ! Message ("node " + nodeId + " is alive !")

        case IsAliveLeader (nodeId) => {
          if(nodeId == this.id) father ! Message ("I'm the Leader !")
          if (leader != nodeId) {
            leader = nodeId
            father ! LeaderChanged (nodeId)
          }
        }

//          father ! Message ("node " + nodeId + " is Leader and is alive !")


        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort
        case CheckerTick => {

          context.system.scheduler.scheduleOnce(this.time milliseconds, self, CheckerTick)

          father ! Message("Tick")
          val toDelete: List[Int] = List()
            for(i <- 0 to nodesAlive.length) {
              if (!nodesChecked.contains(i)) {
                if(i == leader) {
                  father ! Message ("Node "+ i + " was leader but is dead !!!!!!!!!!!!!!!!!")
                }
                else father ! Message ("Node "+ i + " is dead !")
                toDelete:::List(i)
              }
            }
          nodesAlive = nodesAlive.filter(n => toDelete.contains(n))
        }

    }


}
