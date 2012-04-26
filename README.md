WarMachine
=====
A very minimal java web app server. It combines [Jetty](http://jetty.codehaus.org/jetty/), [Jasper](http://tomcat.apache.org/tomcat-6.0-doc/jasper-howto.html), [Gson](http://code.google.com/p/google-gson/) and [Logback](http://logback.qos.ch/) to let you host war files from a single jar.

Why?
----
A mix between annoyances with existing application servers and itch scratching. I was a bit interesting to make at least.

How do I use it?
----------------
1. Build the jar

        mvn install
2. Wait for maven to download, build, unpack and repack everything
3. Run

        java -jar target/war-machine-1.0.jar

However this will not do much, it just starts a server that serves 404s on port 8080. To fix this you need to add wars, try this:

        java -jar target/war-machine-1.0.jar /hello=src/test/resources/war-project-1.0.war

This will load the war using the context path /hello. You can add any number of additional wars in the same way. But now let's get to the fun part.

        java -jar target/war-machine-1.0.jar /1=src/test/resources/war-project-1.0.war /2=src/test/resources/war-project-2.0.war -p 9090 --package my_server.jar

No server will be started, instead you get another file called my_server.jar and if you run that

        java -jar my_server.jar

you will get the same resulting server as if you had run the previous command without the --package part on the end.

And that's about it, have fun!
