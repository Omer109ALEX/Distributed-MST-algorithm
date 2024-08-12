package projects.maman15.nodes.edges;

import projects.maman15.CustomGlobal;
import projects.maman15.nodes.nodeImplementations.GHSNode;
import sinalgo.nodes.edges.BidirectionalEdge;
import sinalgo.tools.statistics.UniformDistribution;

import java.awt.*;

//A weighted edge which its weight is uniformly selected from [1, 1000000000].
public class EdgeWithWeight extends BidirectionalEdge {
    public static int MIN_WEIGHT = 1, MAX_WEIGHT = 1000000000;

    int weight;
    public static UniformDistribution weightDist = new UniformDistribution(MIN_WEIGHT, MAX_WEIGHT);

    //return A random weight from [1, 1000000000]
    public static int generateRandomWeight() {
        return (int) weightDist.nextSample();
    }

    //Initialize the weight of edge 
    @Override
    public void initializeEdge() {
        super.initializeEdge();
        Integer weight = CustomGlobal.getWeight(startNode.ID, endNode.ID);
        if (weight == null) {
            weight = generateRandomWeight();
        }
        this.weight = weight;
    }


    public int getWeight() {
        return weight;
    }


    @Override
    public String toString() {
        return "Weight: " + getWeight();
    }


    @Override
    public Color getColor() {
        GHSNode start = (GHSNode) startNode, end = (GHSNode) endNode;

        if (CustomGlobal.hasFoundMST()) {
            // If there is a going on message on this edge, mark it red
            if (numberOfMessagesOnThisEdge > 0 || oppositeEdge.numberOfMessagesOnThisEdge > 0) {
                return Color.yellow;
            }
            // If the edge is in the MST and doesn't have a message on it, mark it
            // This is determined by whether one of the nodes is the parent of the other
            if ((start.getParent() != null && start.getParent().equals(end)) || (end.getParent() != null && end.getParent().equals(start))) {
                return Color.RED;
            }

            return Color.BLACK;
        }

        if (numberOfMessagesOnThisEdge > 0 || oppositeEdge.numberOfMessagesOnThisEdge > 0) {
            return Color.yellow;
        }
        return Color.BLACK;
    }
}
