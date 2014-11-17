# reactiveStreamPlay

## What is it?
reactiveStream play is a toy project trying out some [reactive application design](http://www.reactivemanifesto.org/) approaches in Scala.

The basic idea is to be able to interactively build a message transformation network out of simple building blocks. Incoming messages, for example a stream of Twitter messages, are forwarded through this network and transformed in various ways in the nodes. This way you can filter messages, analyse them or mutate them in a flexible manner. All of this is controlled from a reactive web-ui.

reactiveStreamPlay primarily relies on the [Akka actor framework](http://akka.io/) and the [Play 2 web-framework](https://www.playframework.com/). Twitter messages are received using the Twitter4j library. The web interface uses [jsPlumb](http://jsplumbtoolkit.com/) for displaying the graph, [bootstrap](http://getbootstrap.com/) with an [extension for editable tables](http://mindmup.github.io/editable-table/) for the basic design as well as [jQuery](http://jquery.com/) to simplify the JavaScript programming. The GUI doesn't attempt to be backwards compatible and hence uses HTML5 as well as CSS3.

Each node in the graph as well as each connection is represented by an Akka actor. Communication between the web-interface and the server uses Server-Sent-Events for server-to-client updates as well as asyncronous REST for client-to-server modifications. All communication from the web-server part to the backend uses Akkas messages.

## How does it look
The picture below shows a graph taking in messages from twitter and performing various transformations and analyses with the message stream.
![Simple example graph](https://raw.githubusercontent.com/hacst/reactiveStreamPlay/master/media/rspexamplegraph.png)

Click the picture below to see a 5 minute demo of reactiveStreamPlay in action.
[![Youtube demo](https://raw.githubusercontent.com/hacst/reactiveStreamPlay/master/media/youtubeplaceholder.png)](https://www.youtube.com/watch?v=fbcc3rwhgec)


