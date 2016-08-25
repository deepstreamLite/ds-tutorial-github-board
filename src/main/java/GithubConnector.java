import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.deepstream.*;
import org.kohsuke.github.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.util.*;
import java.util.List;

public class GithubConnector {

    private GitHub gitHub;
    private DeepstreamClient deepstreamClient = null;

    public static void main(String[] args) throws IOException {
        String deepstreamURI = "0.0.0.0:6021";
        String webhookURI = "https://NGROK_TUNNEL_URL.ngrok.io";
        String repo = "NAME/REPO";
        new GithubConnector(deepstreamURI, webhookURI, repo);
    }

    public GithubConnector(String deepstreamURI, String webhookURI, String repo) throws IOException {
        try {
            deepstreamClient = new DeepstreamClient(deepstreamURI);
            deepstreamClient.login(new JsonObject());
        } catch (URISyntaxException e) {
            System.out.format("Malformed deepstream URI '%s'%n", deepstreamURI);
            throw new RuntimeException(e);
        } catch (DeepstreamLoginException e) {
            System.out.println("Unable to login to deepstream server%n");
            throw new RuntimeException(e);
        }

        System.out.println("Connected to deepstream successfully");

        // connect to github
        gitHub = GitHub.connect();
        GHRepository repository = gitHub.getRepository(repo);

        // labels that will be used to maintain lists
        List<String> interestingLabels = Arrays.asList(
                "roadmap", "ready", "in progress", "awaiting review", "in review");

        // add label list to deepstream
        deepstreamClient.record.getList("github-board-labels")
                .setEntries(interestingLabels);

        initializeIssueLists(repository, interestingLabels);

        setupIssueColors(repository, interestingLabels);

        // cleanup any pre-existing web hooks
        for (GHHook hook : repository.getHooks()) {
            hook.delete();
        }

        // start the web hook listener
        startServer();

        // subscribe to webhooks for Issue update events
        List<GHEvent> events = Arrays.asList(GHEvent.ISSUES);
        repository.createWebHook(new URL(webhookURI), events);

    }

    private GHLabel getLabelWithName(List<GHLabel> repoLabels, String labelName) {
        for (GHLabel label: repoLabels) {
            if(label.getName().equals(labelName)) {
                return label;
            }
        }
        return null;
    }

    private void initializeIssueLists(GHRepository repository, List<String> interestingLabels) throws IOException {
        HashSet<String> interestingLabelSet = new HashSet<>(interestingLabels);

        // initialize issue lists
        for (String labelName: interestingLabels) {
            deepstreamClient.record.getList(labelName).setEntries(new ArrayList<String>());
        }

        // get open issues
        List<GHIssue> issues = repository.getIssues(GHIssueState.OPEN);

        // put the issues into lists by label
        for (GHIssue issue: issues){
            String issueId = Integer.toString(issue.getId());
            for (GHLabel label : issue.getLabels()) {
                String labelName = label.getName();
                if (interestingLabelSet.contains(labelName)){
                    // add the issue id to the label list
                    deepstreamClient.record.getList(labelName)
                            .addEntry(issueId);

                    // add a record for the issue
                    deepstreamClient.record.getRecord(issueId)
                            .set("title", issue.getTitle())
                            .set("url", issue.getHtmlUrl())
                            .discard();

                    System.out.format("Added issue %s to list %s%n", issueId, labelName);
                }
            }
        }
    }

    private void setupIssueColors(GHRepository repository, List<String> interestingLabels) throws IOException {
        // set the label colors
        List<GHLabel> repoLabels = repository.listLabels().asList();
        Record labelColorsRecord = deepstreamClient.record.getRecord("github-board-label-colors");

        for (String labelName: interestingLabels) {
            GHLabel label = getLabelWithName(repoLabels, labelName);
            if (label != null)
                // add records for each label's color
                labelColorsRecord.set(labelName, label.getColor());
            else {
                System.out.printf("Label '%s' does not exist on the repository", labelName);
            }
        }
    }

    public void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new RequestHandler() );
        server.setExecutor(null);
        server.start();
    }

    class RequestHandler implements HttpHandler {

        public void handle(HttpExchange httpExchange) throws IOException {
            InputStreamReader isr = new InputStreamReader(httpExchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            String query = br.readLine();
            String payload = URLDecoder.decode(query, "utf-8").replaceFirst("payload=", "");

            Gson gson = new Gson();
            JsonObject issueEvent = gson.fromJson(payload, JsonObject.class);

            String action = issueEvent.get("action").getAsString();

            JsonObject issue = issueEvent.get("issue").getAsJsonObject();
            String issueId = issue.get("id").getAsString();

            if (action.equals("edited")){
                String issueTitle = issue.get("title").getAsString();
                String issueUrl = issue.get("html_url").getAsString();

                deepstreamClient.record.getRecord(issueId)
                        .set("title", issueTitle)
                        .set("url", issueUrl);
            } else if (action.equals("labeled")) {
                deepstreamClient.record
                        .getList(issueEvent.get("label").getAsJsonObject().get("name").getAsString())
                        .addEntry(issueId);
            } else if (action.equals("unlabeled")) {
                deepstreamClient.record
                        .getList(issueEvent.get("label").getAsJsonObject().get("name").getAsString())
                        .removeEntry(issueId);
            }

            httpExchange.sendResponseHeaders(200, 0);
            OutputStream os = httpExchange.getResponseBody();
            os.close();
        }
    }
}
