package projects.maman15.nodes.messages;

import projects.maman15.nodes.nodeImplementations.GHSNode;
import sinalgo.nodes.messages.Message;

import java.util.Vector;

/**
 * A message that is sent all the way up to the server.
 * This message represents a request from the server.
 */
public class RequestMessage extends Message {
    String message;
    Vector<GHSNode> route;

    public String getMessage() {
        return message;
    }

    public Vector<GHSNode> getRoute() {
        return route;
    }

    /**
     * A constructor that initializes the route with the given node.
     *
     * @param message The content of the request
     * @param sender  The sender of the request, i.e. the first node in the route
     */
    public RequestMessage(String message, GHSNode sender) {
        this.message = message;

        route = new Vector<>();
        route.add(sender);
    }

    /**
     * A constructor that generates a request from another request by adding a node to its route.
     *
     * @param receivedMessage The other request
     * @param currentNode     The node to add
     */
    public RequestMessage(RequestMessage receivedMessage, GHSNode currentNode) {
        this.message = receivedMessage.message;

        route = new Vector<>(receivedMessage.route);
        route.add(currentNode);
    }

    /**
     * A copy constructor for the clone method
     *
     * @param other The other RequestMessage object
     */
    public RequestMessage(RequestMessage other) {
        message = other.message;

        route = new Vector<>(other.route);
    }

    @Override
    public Message clone() {
        return new RequestMessage(this);
    }
}
