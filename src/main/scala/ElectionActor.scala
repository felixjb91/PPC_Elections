package upmc.akka.leader

import java.util.Date

import akka.actor._

abstract class NodeStatus
case class Passive () extends NodeStatus
case class Candidate () extends NodeStatus
case class Dummy () extends NodeStatus
case class Waiting () extends NodeStatus
case class Leader () extends NodeStatus

abstract class LeaderAlgoMessage
case class Initiate () extends LeaderAlgoMessage
case class ALG (nodeId:Int) extends LeaderAlgoMessage
case class AVS (nodeId:Int) extends LeaderAlgoMessage
case class AVSRSP (nodeId:Int) extends LeaderAlgoMessage

case class StartWithNodeList (list:List[Int], init:Int)

class ElectionActor (val id:Int, val terminaux:List[Terminal]) extends Actor {

     val father = context.parent
     var nodesAlive:List[Int] = List(this.id)

     var candSucc:Int = -1
     var candPred:Int = -1
     var status:NodeStatus = new Candidate ()

     var neigh:Int = -1

     var timer:Int = 0
     var electionBegin: Boolean = false

     def receive = {

          // Initialisation
          case Start => {
               self ! Initiate
          }

          case StartWithNodeList (list, init) => {
              if(!electionBegin) {
                 electionBegin = true
                 father ! DontStartAgain
                 father ! Message ("list nodes 1 : "+list + " , from " + init)
                 if (list.isEmpty) {
                      this.nodesAlive = this.nodesAlive:::List(id)
                 }
                 else {
                      this.nodesAlive = list
                 }
                 father ! Message ("list nodes 2 : "+ nodesAlive)
                 this.actualiseNeigh()

                 // Debut de l'algorithme d'election
                 self ! Initiate
              }
          }

          case Initiate => {
            if(timer <= 1000)
            {
              timer = timer + 1
              Thread.sleep(1)
              self ! Initiate
            }
            else {

              father ! Message("Begin intiate -> " + status)

              //this.status = new Candidate
              getNode(neigh) ! ALG(this.id)
            }
          }

          case ALG (init) => {

               if(electionBegin) {
                    father ! Message("Begin ALG from " + init + " -> " + status+ " , candSucc : "+candSucc+ " , candPred : "+candPred)

                    this.status match {
                         case Passive() => {
                              this.status = new Dummy
                              father ! Message(id + " send ALG to " + neigh + " (" + init + ")")
                              getNode(neigh) ! ALG(init)
                         }
                         case Candidate() => {
                              this.candPred = init
                              if (this.id > init) {
                                   if (this.candSucc == -1) {
                                        this.status = new Waiting
                                        father ! Message(id + " send AVS to " + init)
                                        getNode(init) ! AVS(this.id)
                                   } else {
                                        father ! Message(id + " send AVSRSP to " + candSucc + " (" + candPred + ")")
                                        getNode(candSucc) ! AVSRSP(candPred)
                                        this.status = new Dummy
                                   }
                              } else if (this.id == init) {
                                   father ! Message(id + " new Leader")
                                   this.status = new Leader
                                   father ! LeaderChanged(this.id)
                                   father ! SetPassive()
                              }
                         }
                         case _ =>
                    }
               }else { father ! Message ("election pas commencé ALG" )}
          }

          case AVS (j) => {

               if(electionBegin) {

                    father ! Message("Begin AVS from " + j+ " -> " + status+ " , candSucc : "+candSucc+ " , candPred : "+candPred)

                    this.status match {
                         case Candidate() => {
                              if (this.candPred == -1) candSucc = j
                              else {
                                   father ! Message(id + " send AVSRSP to " + j + " (" + candPred + ")")
                                   getNode(j) ! AVSRSP(candPred)
                                   this.status = new Dummy
                              }
                         }
                         case Waiting() => candSucc = j
                         case _ =>
                    }
               } else { father ! Message ("election pas commencé")}
          }

          case AVSRSP (k) => {

               if(electionBegin) {

                    father ! Message("Begin AVSRSP from " + k+ " -> " + status+ " , candSucc : "+candSucc+ " , candPred : "+candPred)

                    this.status match {
                         case Waiting() => {
                              if (this.id == k) {
                                   this.status = new Leader
                                   father ! Message(id + " new Leader")
                                   father ! LeaderChanged(this.id)
                              }
                              else {
                                   candPred = k
                                   if (candSucc == -1) {
                                        if (k < this.id) {
                                             this.status = new Waiting
                                             father ! Message(id + " send AVS to " + k + " (" + id + ")")
                                             getNode(k) ! AVS(this.id)
                                        } else {
                                             this.status = new Dummy

                                             father ! Message(id + " send AVSRSP to " + candSucc + " (" + k + ")")
                                             getNode(candSucc) ! AVSRSP(k)
                                        }
                                   }
                              }
                         }
                         case _ =>
                    }
               }else { father ! Message ("election pas commencé")}
          }
          case _ =>
     }

     def getNode (nodeId: Int): ActorSelection = {
          val n = this.terminaux(nodeId);
          context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node")
     }

     def actualiseNeigh(): Unit = {
          val indice = this.nodesAlive.indexOf(this.id)
          if (this.nodesAlive.length-1 > indice) {
               this.neigh = this.nodesAlive(indice+1)
          } else this.neigh = this.nodesAlive.head

//          father ! Message("neigh : " + neigh)
//          father ! Message("my indice : " + indice + " , last indice : " + (nodesAlive.length-1) + " , neigh : " + this.nodesAlive(indice+1) + " , nbElem : " +this.nodesAlive.length)

     }

}
