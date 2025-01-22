package ecommercewebsite;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;


import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

public class EcommerceWebsite extends Simulation {

    FeederBuilder<String> feeder = csv("messages.csv").random();
    
    HttpProtocolBuilder httpProtocol =
          http.baseUrl("<YOUR_KOYEB_URL>")
          .sseUnmatchedInboundMessageBufferSize(1000000000);

    ScenarioBuilder prompt = scenario("Scenario").exec(
          feed(feeder),
          sse("Connect to App and get Answer")
          .post("/chat")
          .header("X-Session-Id", "#{randomUuid()}")
          .header("X-Message","#{message}")
          .asJson(),

          asLongAs("#{stop.isUndefined()}").on(
              sse.processUnmatchedMessages((messages, session) -> {
              return messages.stream()
              .anyMatch(message -> message.message().contains("{\"data\":\"[DONE]\"}")) ? session.set("stop", true) : session;
              })
          ),

          sse("close").close()
    );


    {
      setUp(
        prompt.injectOpen(
         atOnceUsers(5)
       ).protocols(httpProtocol)).assertions(
        global().responseTime().max().lt(50),
        global().successfulRequests().percent().gt(95.0));
    }

}
