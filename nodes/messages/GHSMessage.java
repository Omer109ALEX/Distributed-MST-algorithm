package projects.maman15.nodes.messages;

import projects.maman15.nodes.nodeImplementations.GHSNode;
import sinalgo.nodes.messages.Message;


public class GHSMessage extends Message {


	MessageType type;
	GHSNode from;
    GHSNode to;
    Integer weight;
    int idChangedOfNeighbor;
	int idUpdateRootFragmentID;
    int numOfNodesInSubtree;
    int startTime;

    
    public GHSMessage(MessageType type) {
        this.type = type;
    }
    
    public GHSMessage(MessageType type, Integer weight) {
    	if (type == MessageType.MWOEChoiceMessage) {
    		this.type = type;
    		this.weight = weight;
    	}
    }
    
    public GHSMessage(MessageType type, GHSNode from, GHSNode to, Integer weight) {
    	if (type == MessageType.ChosenMWOEMessage) {
    		this.type = type;
            this.from = from;
            this.to = to;
            this.weight = weight;
    	}
    	
    	if (type == MessageType.MWOESuggestionMessage) {
    		this.type = type;
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.numOfNodesInSubtree = 0;
    	}
    	
    }
    
    public GHSMessage(MessageType type, GHSNode from, GHSNode to, Integer weight, int numOfNodesInSubtree) {
    	if (type == MessageType.MWOESuggestionMessage) {
    		this.type = type;
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.numOfNodesInSubtree = numOfNodesInSubtree;
    	}
    	
    }
    
    public GHSMessage(MessageType type, int num) {
    	if (type == MessageType.FragmentIDMessage) {
    		this.type = type;
    		this.idChangedOfNeighbor = num;
    	}
    	if (type == MessageType.FragmentIDUpdateMessage) {
    		this.type = type;
    		this.idUpdateRootFragmentID = num;
    	}
    	if (type == MessageType.StartServerReroutingMessage) {
    		this.type = type;
    		this.startTime = num;
    	}
    	
    }
    
    public GHSMessage(GHSMessage other) {
    	if ((type == MessageType.ChosenMWOEMessage) && (other.getType() == MessageType.ChosenMWOEMessage)) {
    		this.type = other.getType();
            this.from = other.getFrom();
            this.to = other.getTo();
            this.weight = other.getWeight();
    	}
    }
    
    public GHSMessage(MessageType type, GHSMessage msg) {
    	if ((type == MessageType.ChosenMWOEMessage) && (msg.getType() == MessageType.MWOESuggestionMessage)) {
    		this.type = type;
            this.from = msg.getFrom();
            this.to = msg.getTo();
            this.weight = msg.getWeight();
    	}
    }



    @Override
    public Message clone() {
    	if (type == MessageType.ChosenMWOEMessage)
    		return new GHSMessage(type, from, to, weight);
    	if (type == MessageType.FlipEdgeDirectionMessage)
    		return new GHSMessage(type);
    	if (type == MessageType.FragmentIDMessage)
    		return new GHSMessage(type, idChangedOfNeighbor);
    	if (type == MessageType.FragmentIDUpdateMessage)
    		return new GHSMessage(type, idUpdateRootFragmentID);
    	if (type == MessageType.MWOEChoiceMessage)
    		return new GHSMessage(type, weight);
    	if (type == MessageType.MWOESuggestionMessage)
    		return new GHSMessage(type, from, to, weight, numOfNodesInSubtree);
    	if (type == MessageType.StartServerReroutingMessage)
    		return new GHSMessage(type, startTime);
        return new GHSMessage(type);
    }
    
	public MessageType getType() {
		return type;
	}

	public GHSNode getFrom() {
    	if (type == MessageType.ChosenMWOEMessage)
    		return from;
    	if (type == MessageType.MWOESuggestionMessage)
    		return from;
    	return null;
	}

	public GHSNode getTo() {
		if (type == MessageType.ChosenMWOEMessage)
    		return to;
		if (type == MessageType.MWOESuggestionMessage)
    		return to;
    	return null;
    }


	public Integer getWeight() {
		if (type == MessageType.ChosenMWOEMessage)
    		return weight;
		if (type == MessageType.MWOEChoiceMessage)
    		return weight;
		if (type == MessageType.MWOESuggestionMessage)
    		return weight;
    	return null;	
    }
	
	public int getId() {
		if (type == MessageType.FragmentIDMessage)
			return idChangedOfNeighbor;
		if (type == MessageType.FragmentIDUpdateMessage) {
			return idUpdateRootFragmentID;
    	}
		return 0;
	}
	
	public int getNumOfNodesInSubtree() {
    	if (type == MessageType.MWOESuggestionMessage)
    		return numOfNodesInSubtree;
    	return 0;
    }

    public void setNumOfNodesInSubtree(int numOfNodesInSubtree) {
    	if (type == MessageType.MWOESuggestionMessage)
    		this.numOfNodesInSubtree = numOfNodesInSubtree;
    }
    
    public int getStartTime() {
    	if (type == MessageType.StartServerReroutingMessage)
    		return startTime;
    	return 0;
    }
	
}


