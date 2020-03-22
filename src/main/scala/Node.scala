package upmc.akka.leader

import akka.actor._

case class Start ()

sealed trait SyncMessage
case class Sync (nodes:List[Int]) extends SyncMessage
case class SyncForOneNode (nodeId:Int, nodes:List[Int]) extends SyncMessage
case class ResetStatus ()
case class StartElection (list:List[Int])
case class SetPassive ()
case class BeatTicks(nodeId: Int)
sealed trait AliveMessage
case class IsAlive (id:Int) extends AliveMessage
case class IsAliveLeader (id:Int) extends AliveMessage
case class DontStartAgain ()

class Node (val id:Int, val terminaux:List[Terminal]) extends Actor {

     // Les differents acteurs du systeme
     var electionActor = context.actorOf(Props(new ElectionActor(this.id, terminaux)), name = "electionActor")
     val checkerActor = context.actorOf(Props(new CheckerActor(this.id, terminaux, electionActor)), name = "checkerActor")
     val beatActor = context.actorOf(Props(new BeatActor(this.id)), name = "beatActor")
     val displayActor = context.actorOf(Props[DisplayActor], name = "displayActor")

     var StartedElection:Boolean = false
//     var allNodesReady:List[ActorSelection] = List()
     var allNodes:List[ActorSelection] = List()

     def receive = {

          // Initialisation
          case Start => {
               displayActor ! Message ("Node " + this.id + " is created")
               checkerActor ! Start
               beatActor ! Start

               // Initilisation des autres remote, pour communiquer avec eux
               terminaux.foreach(n => {
                    if (n.id != id) {
                         val remote = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node")
                         // Mise a jour de la liste des nodes
                         this.allNodes = this.allNodes:::List(remote)
                    }
               })
          }

          // Envoi de messages (format texte)
          case Message (content) => {
               displayActor ! Message (content)
          }

          case BeatTicks (nodeId) => {
               allNodes.foreach(n => {
                    if(this.id == nodeId) n ! IsAliveLeader(this.id)
                    else n ! IsAlive(this.id)
               })
          }
          case BeatLeader (nodeId) => allNodes.foreach(n  => n ! IsAliveLeader(this.id))

          case Beat (nodeId) => allNodes.foreach(n  => n ! IsAlive(this.id))


          // Messages venant des autres nodes : pour nous dire qui est encore en vie ou mort
          case IsAlive (nodeId) => checkerActor ! IsAlive (nodeId)

          case IsAliveLeader (nodeId) => checkerActor ! IsAliveLeader (nodeId)


          // Message indiquant que le leader a change
          case LeaderChanged (nodeId) => {
               beatActor ! LeaderChanged(nodeId)
               self ! Message("Node " + nodeId + " is the new Leader !")
          }


//        Election methodes
          case ALG (init) => electionActor ! ALG(init)

          case AVS (j) => electionActor ! AVS(j)

          case AVSRSP (k) => electionActor ! AVSRSP(k)


//        Start Election
          case StartWithNodeList (list, init) => if(!StartedElection) electionActor ! StartWithNodeList(list, init)

          case StartElection (list) => if(!StartedElection)  {
               electionActor ! StartWithNodeList(list, this.id)
               allNodes.foreach(n => n ! StartWithNodeList(list, this.id))
          }

          case DontStartAgain => {
               self ! Message ("StartedElection")
               StartedElection = true
          }


//        Reset
          case ResetStatus => allNodes.foreach(n => n ! SetPassive)

          case SetPassive => {
               this.electionActor = context.actorOf(Props(new ElectionActor(this.id, terminaux)), name = "electionActor")
               StartedElection = false
          }

     }

     def getNode (nodeId: Int): ActorSelection = {
          val n = this.terminaux(nodeId)
          return context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node")
     }

}


