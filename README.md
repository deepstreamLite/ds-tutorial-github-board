# GitHub Issue Board Tutorial

A simple GitHub issue tracker similar to [waffle.io](//waffle.io/) that shows
active issues on a project with specific workflow labels, updating in realtime
as issues are created and updated on GitHub. This post will focus on building a
deepstream connector in Java using deepstream's Java API and [Kohsuke
Kawaguchi's GitHub API for Java](//github-api.kohsuke.org/).

## To run:

- Within the client sub-directory, run `npm install` to install the client
  dependencies, then run `python -m SimpleHTTPServer` to start a test server.
  You should then be able to view the client at
  [localhost:8000](//localhost:8000/).

- Install and start a local deepstream server. For more information 
see the [Quickstart Tutorial](//deepstream.io/tutorials/core/getting-started-quickstart/).

- Create a [GitHub personal access token](//github.com/settings/tokens/) with 
repository access. Then create a file `~/.github` containing:
```
oauth={your personal access token}
``` 
It's also possible to put your github credentials in this file to avoid this
step – [see here](//http://github-api.kohsuke.org//).

- Install [ngrok](//ngrok.com/) which will allow us to listen for external connections
from the webhook we'll use later on. Start it using `ngrok http 8080`, and make note of the
forwarding address.

- I recommend setting up a test GitHub repository with some issues, and labelling
some of them with 'roadmap', 'ready', 'in progress', 'awaiting review' or 'in review'. 
It's also a good idea to give those labels some nice colors – we'll be using those later on.

- Place the ngrok URI and repository path in `src/main/java/GithubConnector.java`.

- Run `./gradlew assemble` to build, or use your IDE's gradle plugin. 
