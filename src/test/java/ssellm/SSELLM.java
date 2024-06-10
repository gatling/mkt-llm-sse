package ssellm;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;


import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;


public class SSELLM extends Simulation {
   String api_key = System.getenv("api_key");

   HttpProtocolBuilder httpProtocol =
      http.baseUrl("https://api.openai.com/v1/chat")
          .sseUnmatchedInboundMessageBufferSize(100);

  ScenarioBuilder prompt = scenario("Scenario").exec(
      sse("Connect to LLM and get Answer")
          .post("/completions")
          .header("Authorization", "Bearer "+api_key)
          .body(StringBody("{\"model\": \"gpt-3.5-turbo\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Just say HI\"}]}"))
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
        prompt.injectOpen(atOnceUsers(10))
    ).protocols(httpProtocol);
  }
}
