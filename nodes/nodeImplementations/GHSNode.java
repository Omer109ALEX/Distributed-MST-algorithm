package projects.maman15.nodes.nodeImplementations;

import projects.maman15.CustomGlobal;
import projects.maman15.nodes.edges.EdgeWithWeight;
import projects.maman15.nodes.messages.*;
import sinalgo.configuration.WrongConfigurationException;
import sinalgo.nodes.Node;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.tools.Tools;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Vector;


public class GHSNode extends Node {
    int fragmentID;    // The current fragment ID of the current node
    HashMap<GHSNode, Integer> fragmentIdOfNeighbors = new HashMap<>();    // The fragment ID of each neighbor during the algorithm
    HashMap<GHSNode, EdgeWithWeight> neighborsEdges = new HashMap<>();    // The edges to each neighbor 
    Vector<GHSNode> children = new Vector<>();    // The children of the node in the MST (empty if it is a leaf)
    GHSNode parent;    // The parent of the node in the MST (null if it is the root)
    GHSMessage mwoeToAdd;    // A temporary member where the root saves the new MWOE to add in every iteration
    public Vector<GHSMessage> mwoeChildrenSuggestions = new Vector<>();	// The queue of the MWOE suggestions of the children.
    public GHSNode parentCandidate;    // The new candidate for a parent (the other edge of the MWOE)
    boolean isRoot, isServer;
    Vector<RequestMessage> pendingReqs = new Vector<>();

    public Vector<GHSNode> getChildren() {
        return children;
    }

    public GHSNode getParent() {
        return parent;
    }

    public void setIsServer(boolean isServer) {
        this.isServer = isServer;
    }

    private Integer getWeightOfEdgeTo(GHSNode neighbor) {
        if (neighbor == null) return null;
        return neighborsEdges.get(neighbor).getWeight();
    }

    public GHSNode getMinimumWeightEdge() {
        int minWeight = EdgeWithWeight.MAX_WEIGHT + 1;
        GHSNode minimumWeightNeighbor = null;
        for (Edge e : outgoingConnections) {
            GHSNode neighbor = (GHSNode) e.endNode;
            if (fragmentIdOfNeighbors.get(neighbor) == fragmentID) continue;
            int weight = getWeightOfEdgeTo(neighbor);
            if (weight < minWeight) {
                minWeight = weight;
                minimumWeightNeighbor = neighbor;
            }
        }
        return minimumWeightNeighbor;
    }


    public void start() {
        // Reset all the records and counters
        fragmentIdOfNeighbors.clear();
        neighborsEdges.clear();
        mwoeChildrenSuggestions.clear();
        children.clear();
        parent = null;
        mwoeToAdd = null;
        roundCounter = 0;
        isRoot = true;
        fragmentID = ID;
        
        // Initialize fragmentIdOfNeighbors and neighborsEdges
        outgoingConnections.forEach(e -> {
            neighborsEdges.put((GHSNode) e.endNode, (EdgeWithWeight) e);
            fragmentIdOfNeighbors.put((GHSNode) e.endNode, e.endNode.ID);
        });

        // Find the first MWOE and change the state to MWOE_SEND to start the algorithm
        parentCandidate = getMinimumWeightEdge();
        currentState = GHSStates.MWOE_SEND;
    }

    /**
     * The possible states of the algorithm.
     */
    enum GHSStates {
        NOT_STARTED, // not initialized yet
        MWOE_SEND, // connect fragments using the chosen MWOE (MWOE == Minimum Weight Outgoing Edge)
        LEADER_DISCOVERY, // find the new root of each fragment (which will be the maximum ID node which chose a node that chose it too)
        FRAGMENT_ID_DISCOVERY, // broadcast the new fragment ID
        MWOE_SEARCHING, // convergecast the MWOE to the root of the fragment
        MWOE_BROADCASTING, // broadcast the MWOE of the fragment which will be added to the MST
        NEW_ROOT_BROADCASTING, // broadcast the new root of the fragment and flip the edges on the route from it to the old root of the fragment
        BUILD_MST_FROM_SERVER, // redirect the edges to make the server the root of the MST
        FINISHED, // the algorithm has finished
        SENDING_MESSAGE_TO_SERVER, // sending a message to the server over the MST
    }

   
    public boolean hasFinished() {
        return currentState == GHSStates.FINISHED;
    }

    public boolean hasFoundMST() {
        return currentState == GHSStates.FINISHED || currentState == GHSStates.SENDING_MESSAGE_TO_SERVER;
    }

    // A round counter to insure each phase (which needs to) takes n rounds
    int roundCounter = 0;
    // The current state of the algorithm at the current node
    public GHSStates currentState = GHSStates.NOT_STARTED;

    /**
     * Switch the current state to the next state, based on the iteration and round counters.
     * These states require 1 round:
     * - MWOE_SEND
     * - LEADER_DISCOVERY
     * These states require n rounds:
     * - FRAGMENT_ID_DISCOVERY
     * - MWOE_SEARCHING
     * - MWOE_BROADCASTING
     * - NEW_ROOT_BROADCASTING
     * The lists are sorted by the order of the states.
     * Once the root notices that all the nodes in the network are in his fragment, the server rerouting starts.
     * The server reRouting takes 2n rounds from the moment the root noticed that all the nodes are in his fragments.
     */
    public void switchState() {
        switch (currentState) {
            case NOT_STARTED:
                currentState = GHSStates.MWOE_SEND;
                break;
            case MWOE_SEND:
                currentState = GHSStates.LEADER_DISCOVERY;
                break;
            case LEADER_DISCOVERY:
                currentState = GHSStates.FRAGMENT_ID_DISCOVERY;
                roundCounter = 0;
                break;
            case FRAGMENT_ID_DISCOVERY:
                if (++roundCounter == CustomGlobal.getNumOfNodes()) {
                    currentState = GHSStates.MWOE_SEARCHING;
                    roundCounter = 0;
                }
                break;
            case MWOE_SEARCHING:
                if (++roundCounter == CustomGlobal.getNumOfNodes()) {
                    currentState = GHSStates.MWOE_BROADCASTING;
                    roundCounter = 0;
                }
                break;
            case MWOE_BROADCASTING:
                if (++roundCounter == CustomGlobal.getNumOfNodes()) {
                    currentState = GHSStates.NEW_ROOT_BROADCASTING;
                    roundCounter = 0;
                }
                break;
            case NEW_ROOT_BROADCASTING:
                if (++roundCounter == CustomGlobal.getNumOfNodes()) {
                    roundCounter = 0;
                    currentState = GHSStates.MWOE_SEND;
                }
                break;
            case BUILD_MST_FROM_SERVER:
                if (++roundCounter == 2 * CustomGlobal.getNumOfNodes()) {
                    currentState = GHSStates.FINISHED;
                    roundCounter = 0;
                }
                break;
            case FINISHED:
                break;
        }
    }

    /**
     * An iteration of the MWOE_SEND state.
     * In this state, if the node is a root (which means its MWOE has been chosen), he connects to the fragment of the node on the other side of the MWOE by sending a MWOEChoiceMessage.
     */
    public void mwoeSendIter(Inbox inbox) {
        // If the current node is the node with the lightest edge that goes out of the fragment, add this edge to the MST and connect the fragments.
        if (isRoot) {
            send(new GHSMessage(MessageType.MWOEChoiceMessage, getWeightOfEdgeTo(parentCandidate)), parentCandidate);
        }
    }

    /**
     * An iteration of the LEADER_DISCOVERY state.
     * In this state, the new root of each fragment is chosen. The new root will be the maximum ID node which the node in the other side of the MWOE chose it too.
     * The children and parent members are also updated according to every node that chose an MWOE which is connected to the current node.
     */
    public void leaderDiscoveryIter(Inbox inbox) {
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            if (((GHSMessage)msg).getType() == MessageType.MWOEChoiceMessage) {
                GHSNode node = (GHSNode) inbox.getSender();
                // Case of the parentCandidate (the node in the other side of the MWOE) choosing you too
                if (node.equals(parentCandidate)) {
                    // If the other node has the maximal ID mark it as this node's parent
                    if (parentCandidate.ID > ID) {
                        parent = parentCandidate;
                        parentCandidate = null;
                        isRoot = false;
                    }
                    // If the current node has the maximal ID mark it as a root
                    else {
                        children.add(node);
                        parentCandidate = null;
                        isRoot = true;
                    }
                }
                // If a node which is not the parentCandidate chooses you as the other side of its MWOE (you are its parentCandidate) add it as your child
                else {
                    children.add(node);
                }
            }
            // Listen for a start server reRouting message
            
            else if (((GHSMessage)msg).getType() == MessageType.StartServerReroutingMessage) {
                for (GHSNode child : children) {
                    send(msg, child);
                }
                startServerRerouting((GHSMessage) msg);
            }
        }
        // If the parentCandidate didn't choose you (very sad) add the chosen MWOE to the fragment and mark it as a parent
        if (parentCandidate != null) {
            parent = parentCandidate;
            parentCandidate = null;
            isRoot = false;
        }
    }

    /**
     * An iteration of the FRAGMENT_ID_DISCOVERY state.
     * In this state, the new root of each fragment broadcasts its ID as the new fragment ID.
     * In addition, each node that receives a new fragment ID updates all its neighbors with the new fragment ID.
     */
    public void fragmentIDDiscoveryIter(Inbox inbox) {
        // If the current node is the root of the fragment, send the node's id to all of its children only if it is the first iteration of this state
        if (isRoot) {
            // If it is the first iteration of this state send the node's id (which is the new fragment id) to its children
            if (roundCounter == 0) {
                fragmentID = ID;
                for (GHSNode child : children) {
                    send(new GHSMessage(MessageType.FragmentIDUpdateMessage, fragmentID), child);
                }
                // Update neighbors about my new fragment id
                for (Edge e : outgoingConnections) {
                    send(new GHSMessage(MessageType.FragmentIDMessage, fragmentID), e.endNode);
                }
            }
            // Listen for updates of the neighbors about their fragment id
            while (inbox.hasNext()) {
                Message msg = inbox.next();
                if (((GHSMessage)msg).getType() == MessageType.FragmentIDMessage) {
                    fragmentIdOfNeighbors.put((GHSNode) inbox.getSender(), ((GHSMessage) msg).getId());
                }
                // Listen for a start server rerouting message
                else if (((GHSMessage)msg).getType() == MessageType.StartServerReroutingMessage) {
                    for (GHSNode child : children) {
                        send(msg, child);
                    }
                    startServerRerouting((GHSMessage) msg);
                }
            }
        }
        // If the current node is not the root of the fragment, update the fragment id according to the received id and pass it to the node's children
        else {
            while (inbox.hasNext()) {
                Message msg = inbox.next();
                if (((GHSMessage)msg).getType() == MessageType.FragmentIDUpdateMessage) {
                    fragmentID = ((GHSMessage) msg).getId();
                    for (GHSNode child : children) {
                        send(msg, child);
                    }
                    // Update neighbors about my new fragment id
                    for (Edge e : outgoingConnections) {
                        send(new GHSMessage(MessageType.FragmentIDMessage,fragmentID), e.endNode);
                    }
                }
                // Listen for updates of the neighbors about their fragment ids
                else if (((GHSMessage)msg).getType() == MessageType.FragmentIDMessage) {
                    fragmentIdOfNeighbors.put((GHSNode) inbox.getSender(), ((GHSMessage) msg).getId());
                }
                // Listen for a start server rerouting message
                else if (((GHSMessage)msg).getType() == MessageType.StartServerReroutingMessage) {
                    for (GHSNode child : children) {
                        send(msg, child);
                    }
                    startServerRerouting((GHSMessage) msg);
                }
            }
        }
    }

    /**
     * An iteration of the MWOE_SEARCHING state.
     * In this state, each node convergecasts the MWOE which connects the node to a node from another fragment.
     * Each node waits for the MWOE suggestions from all of its children to be received, and then chooses the minimal from those and from its MWOE and forwards it to its parent.
     * In addition, each node sends the number of nodes in the subtree which it is the root of. Each node sums the values of its children and adds 1, and this is the value it forwards its parent.
     * The root of every fragment will eventually get the number of nodes in its fragment, and if it is n the algorithm will be finished by broadcasting start server rerouting messages.
     */
    public void mwoeSearchingIter(Inbox inbox) {
        if (children.isEmpty()) {
            if (roundCounter == 0) {
                GHSNode mwoeNode = getMinimumWeightEdge();
                // If mwoeNode == null, getWeightOfEdgeTo(mwoeNode) == null and it is fine
                // We must do it because the parent must receive one message from every child
                send(new GHSMessage(MessageType.MWOESuggestionMessage, this, mwoeNode, getWeightOfEdgeTo(mwoeNode), 1), parent);
            }
            while (inbox.hasNext()) {
                Message msg = inbox.next();
                if (((GHSMessage)msg).getType() == MessageType.StartServerReroutingMessage) {
                    startServerRerouting((GHSMessage) msg);
                }
            }
        } else {
            while (inbox.hasNext()) {
                Message msg = inbox.next();
                if (((GHSMessage)msg).getType() == MessageType.MWOESuggestionMessage) {
                    mwoeChildrenSuggestions.add((GHSMessage) msg);
                }
                // Listen for a start server reRouting message
                else if (((GHSMessage)msg).getType() == MessageType.StartServerReroutingMessage) {
                    for (GHSNode child : children) {
                        send(msg, child);
                    }
                    startServerRerouting((GHSMessage) msg);
                }
            }
            if (mwoeChildrenSuggestions.size() == children.size()) {
                GHSNode mwoeNode = getMinimumWeightEdge();
                Integer min_weight = getWeightOfEdgeTo(mwoeNode);
                GHSMessage mwoeSuggestionToSend = new GHSMessage(MessageType.MWOESuggestionMessage,this, mwoeNode, min_weight);
                // Initialized to 1 to include the current node in the count
                int nodesInSubtreeCounter = 1;
                if (min_weight == null) min_weight = EdgeWithWeight.MAX_WEIGHT;
                for (GHSMessage mwoeMsg : mwoeChildrenSuggestions) {
                    nodesInSubtreeCounter += mwoeMsg.getNumOfNodesInSubtree();
                    Integer currWeight = mwoeMsg.getWeight();
                    if (currWeight == null) continue;
                    if (currWeight < min_weight) {
                        min_weight = currWeight;
                        mwoeSuggestionToSend = mwoeMsg;
                    }
                }
                mwoeSuggestionToSend.setNumOfNodesInSubtree(nodesInSubtreeCounter);
                if (isRoot) {
                    // If all the nodes are in the subtree of the current root, the algorithm is finished
                    if (nodesInSubtreeCounter == CustomGlobal.getNumOfNodes()) {
                    	GHSMessage msg = new GHSMessage(MessageType.StartServerReroutingMessage,(int) Tools.getGlobalTime());
                        for (GHSNode child : children) {
                            send(msg, child);
                        }
                        startServerRerouting(msg);
                    }
                    mwoeToAdd = mwoeSuggestionToSend;
                } else {
                    send(mwoeSuggestionToSend, parent);
                }
                mwoeChildrenSuggestions.clear();
            }
        }
    }

    /**
     * An iteration of the MWOE_BROADCASTING state.
     * In this state, the root broadcasts the chosen MWOE that goes out of the current fragment.
     * Each node will receive the message, and if it is its MWOE it will update its parentCandidate member.
     */
    public void mwoeBroadcastingIter(Inbox inbox) {
        if (isRoot) {
            if (roundCounter == 0) {
                if (mwoeToAdd == null) return;
                // If this is the current node's MWOE, update parentCandidate
                if (mwoeToAdd.getFrom().equals(this)) {
                    parentCandidate = mwoeToAdd.getTo();
                } else if (mwoeToAdd.getTo().equals(this)) {
                    parentCandidate = mwoeToAdd.getFrom();
                }
                // Otherwise, update the children about the MWOE
                else {
                    for (GHSNode child : children) {
                        send(new GHSMessage(MessageType.ChosenMWOEMessage, mwoeToAdd), child);
                    }
                }
                mwoeToAdd = null;
            }
        } else {
            while (inbox.hasNext()) {
                Message msg = inbox.next();
                if (((GHSMessage)msg).getType() == MessageType.ChosenMWOEMessage) {
                	GHSMessage chosenMWOEMsg = (GHSMessage) msg;
                    // If this is the current node's MWOE, update parentCandidate
                    if (chosenMWOEMsg.getFrom().equals(this)) {
                        parentCandidate = chosenMWOEMsg.getTo();
                    } else if (chosenMWOEMsg.getTo().equals(this)) {
                        parentCandidate = chosenMWOEMsg.getFrom();
                    }
                    // Otherwise, update the children about the MWOE
                    else {
                        for (GHSNode child : children) {
                            send(chosenMWOEMsg, child);
                        }
                    }
                }
                // Listen for a start server reRouting message
                else if (((GHSMessage)msg).getType() == MessageType.StartServerReroutingMessage) {
                    for (GHSNode child : children) {
                        send(msg, child);
                    }
                    startServerRerouting((GHSMessage) msg);
                }
            }
        }
    }

    /**
     * An iteration of the NEW_ROOT_BROADCASTING state.
     * In this state, the node which the fragment's MWOE is connected to will become the new root of the fragment.
     * It will send a FlipEdgeDirectionMessage to its parent, and each node that receives this message will change its parent to be the sender and forward it to its old parent.
     * This will continue all the way up to the old root of the fragment.
     */
    public void newRootBroadcastingIter(Inbox inbox) {
        if (roundCounter == 0 && parentCandidate != null) {
            if (!isRoot) {
                children.add(parent);
                send(new GHSMessage(MessageType.FlipEdgeDirectionMessage), parent);
                parent = null;
                isRoot = true;
            }
        }
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            if (((GHSMessage)msg).getType() == MessageType.FlipEdgeDirectionMessage) {
                if (!isRoot) {
                    children.add(parent);
                    send(msg, parent);
                } else {
                    isRoot = false;
                }
                parent = (GHSNode) inbox.getSender();
                children.remove(parent);
            }
            // Listen for a start server rerouting message
            else if (((GHSMessage)msg).getType() == MessageType.StartServerReroutingMessage) {
                for (GHSNode child : children) {
                    send(msg, child);
                }
                startServerRerouting((GHSMessage) msg);
            }
        }
    }

    /**
     * An iteration of the BUILD_MST_FROM_SERVER state.
     * In this state, the edges on the route from the root to the server will be flipped in order to make the server the new root of the MST.
     */
    public void serverReroutingIter(Inbox inbox) {
        if (isServer && !isRoot) {
            children.add(parent);
            send(new GHSMessage(MessageType.FlipEdgeDirectionMessage), parent);
            parent = null;
            isRoot = true;
        }
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            if (((GHSMessage)msg).getType() == MessageType.FlipEdgeDirectionMessage) {
                if (!isRoot) {
                    children.add(parent);
                    send(msg, parent);
                } else {
                    isRoot = false;
                }
                parent = (GHSNode) inbox.getSender();
                children.remove(parent);
            }
            // Listen for a start server reRouting message
            else if (((GHSMessage)msg).getType() == MessageType.StartServerReroutingMessage) {
                for (GHSNode child : children) {
                    send(msg, child);
                }
                startServerRerouting((GHSMessage) msg);
            }
        }
    }

    /**
     * An iteration of the SENDING_MESSAGE_TO_SERVER state. This method will also be used in the FINISHED state.
     * This is because nodes which didn't sent a request to the server and aren't in the SENDING_MESSAGE_TO_SERVER state need to pass the requests and responses of other nodes in the SENDING_MESSAGE_TO_SERVER state.
     * In this function, the nodes will pass requests to their parent, will pass responses to one of their children using the route that is given in the message.
     * The server will receive requests and create responses to send.
     */
    public void sendingMessageToServerIter(Inbox inbox) {
        // If there is a request to initiate, send it to the parent of the current node
        if (!pendingReqs.isEmpty()) {
            for (RequestMessage req : pendingReqs) {
                send(req, parent);
            }
            pendingReqs.clear();
        }
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            // Pass requests or handle them, depending on whether this node is the server or not
            if (msg instanceof RequestMessage) {
                RequestMessage req = (RequestMessage) msg;
                if (isServer) {
                    Vector<GHSNode> route = req.getRoute();
                    GHSNode prevInRoute = route.lastElement();
                    // Send the response generated from this request to the previous node in the route
                    send(new ResponseMessage(req), prevInRoute);
                } else {
                    // Send the request to the parent, so it will reach the server. The used constructor of RequestMessage will add `this` to the route of req
                    send(new RequestMessage(req, this), parent);
                }
            }
            // Pass responses or handle them, depending on whether this node is the origin of the request or not
            else if (msg instanceof ResponseMessage) {
                ResponseMessage resp = (ResponseMessage) msg;
                Vector<GHSNode> route = resp.getRoute();
                // If it is the current node's response handle it
                if (route.size() == 1 && route.get(0).equals(this)) {
                    endMessageSending(resp.getMessage());
                }
                // Remove this from the route and pass the response to the previous node in the route
                else {
                    GHSNode thisInRoute = route.lastElement();
                    route.remove(thisInRoute);
                    GHSNode prevInRoute = route.lastElement();
                    send(new ResponseMessage(resp.getMessage(), route), prevInRoute);
                }
            }
        }
    }
    
    public void startServerRerouting(GHSMessage msg) {
    	if (msg.getType() == MessageType.StartServerReroutingMessage) {
    		currentState = GHSStates.BUILD_MST_FROM_SERVER;
            roundCounter = ((int) Tools.getGlobalTime()) - msg.getStartTime();
    	}
    }


    public void startMessageSending(String message) {
        PrintStream board = Tools.getTextOutputPrintStream();
        currentState = GHSStates.SENDING_MESSAGE_TO_SERVER;
        board.println("-------------------------------");
        board.println("Sending [" + message + "] to the server");

        if (isServer) {
            endMessageSending(ResponseMessage.generateServerResponse(message));
            return;
        }
        pendingReqs.add(new RequestMessage(message, this));
    }

    public void endMessageSending(String response) {
        PrintStream board = Tools.getTextOutputPrintStream();
        currentState = GHSStates.FINISHED;
        board.println("Server responded with:\n" + response);
    }


    /**
     * The logic of the node which will be executed in every round.
     * This function initializes the node if it is not initialized, calls the function of the relevant state and switches the state.
     */
    @Override
    public void handleMessages(Inbox inbox) {
        if (currentState == GHSStates.NOT_STARTED) {
            start();
        }
        switch (currentState) {
            case MWOE_SEND:
                mwoeSendIter(inbox);
                break;
            case LEADER_DISCOVERY:
                leaderDiscoveryIter(inbox);
                break;
            case FRAGMENT_ID_DISCOVERY:
                fragmentIDDiscoveryIter(inbox);
                break;
            case MWOE_SEARCHING:
                mwoeSearchingIter(inbox);
                break;
            case MWOE_BROADCASTING:
                mwoeBroadcastingIter(inbox);
                break;
            case NEW_ROOT_BROADCASTING:
                newRootBroadcastingIter(inbox);
                break;
            case BUILD_MST_FROM_SERVER:
                serverReroutingIter(inbox);
                break;
            case SENDING_MESSAGE_TO_SERVER:
            case FINISHED:
                sendingMessageToServerIter(inbox);
                break;
        }
        switchState();
    }

    @Override
    public void init() {
    }

    @Override
    public void neighborhoodChange() {
    }

    @Override
    public void preStep() {
    }

    @Override
    public void postStep() {
    }

    @Override
    public void checkRequirements() throws WrongConfigurationException {
    }


    public void printParent() {
        PrintStream board = Tools.getTextOutputPrintStream();
        if (parent == null) {
        	board.println("Node " + ID + " is the root");
        } else {
        	board.println("Parent of " + ID + " is " + parent.ID);
        }
    }

}
