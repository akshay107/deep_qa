package org.allenai.dlfa.solver

import org.allenai.dlfa.data.Instance
import org.allenai.dlfa.data.BackgroundInstance
import org.allenai.dlfa.data.QuestionAnswerInstance
import org.allenai.dlfa.data.MultipleTrueFalseInstance
import org.allenai.dlfa.data.TrueFalseInstance
import org.allenai.dlfa.message.SolverServiceGrpc.SolverServiceBlockingStub
import org.allenai.dlfa.message.{Instance => MessageInstance}
import org.allenai.dlfa.message.InstanceType
import org.allenai.dlfa.message.SolverServiceGrpc
import org.allenai.dlfa.message.QuestionRequest
import org.allenai.dlfa.message.QuestionResponse

import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder

import java.util.concurrent.TimeUnit

object Client {

  val config = com.typesafe.config.ConfigFactory.load()

  val host = config.getString("grpc.dlfa.server")
  val port = config.getInt("grpc.dlfa.port")

  def showScores(scores: Seq[Double]) = {
    println(s"Scores: [${scores.mkString(" ")}]")
  }

  def main(args: Array[String]) {
    val channel: ManagedChannel =
      OkHttpChannelBuilder.forAddress(host, port).usePlaintext(true).build
    val blockingStub = SolverServiceGrpc.blockingStub(channel)
    val client: Client = new Client(channel, blockingStub)

    val instance = MultipleTrueFalseInstance(Seq(
      BackgroundInstance(TrueFalseInstance("statement 1", None), Seq("background 1")),
      BackgroundInstance(TrueFalseInstance("statement 2", None), Seq("background 2")),
      BackgroundInstance(TrueFalseInstance("statement 3", None), Seq("background 3")),
      BackgroundInstance(TrueFalseInstance("statement 4", None), Seq("background 4"))
    ), None)
    showScores(client.answerQuestion(instance))
  }
}

class Client(
  private val channel: ManagedChannel,
  private val blockingStub: SolverServiceBlockingStub
) {
  def shutdown(): Unit = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  def answerQuestion(instance: Instance): Seq[Double] = {
    val response = sendMessage(instanceToMessage(instance))
    response.scores
  }

  def instanceToMessage(instance: Instance): MessageInstance = {
    instance match {
      case i: BackgroundInstance[_] => {
        val containedMessage = instanceToMessage(i.containedInstance)
        val instanceType = containedMessage.`type`
        val questionText = containedMessage.question
        val answerOptions = containedMessage.answerOptions
        val background = i.background
        MessageInstance(instanceType, questionText, answerOptions, background, Seq())
      }
      case i: QuestionAnswerInstance => {
        val instanceType = InstanceType.QUESTION_ANSWER
        val questionText = i.question
        val answerOptions = i.answers
        MessageInstance(instanceType, questionText, answerOptions, Seq(), Seq())
      }
      case i: MultipleTrueFalseInstance[_] => {
        val instanceType = InstanceType.MULTIPLE_TRUE_FALSE
        val containedInstances = i.instances.map(instanceToMessage)
        MessageInstance(instanceType, "", Seq(), Seq(), containedInstances)
      }
      case i: TrueFalseInstance => {
        val instanceType = InstanceType.TRUE_FALSE
        val questionText = i.statement
        val answerOptions = Seq()
        MessageInstance(instanceType, questionText, answerOptions, Seq())
      }
    }
  }

  def sendMessage(message: MessageInstance): QuestionResponse = {
    val request: QuestionRequest = QuestionRequest(Some(message))
    blockingStub.answerQuestion(request)
  }
}
