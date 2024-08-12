/*
 Copyright (c) 2007, Distributed Computing Group (DCG)
                    ETH Zurich
                    Switzerland
                    dcg.ethz.ch

 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 - Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the
   distribution.

 - Neither the name 'Sinalgo' nor the names of its contributors may be
   used to endorse or promote products derived from this software
   without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package projects.maman15;


import projects.maman15.nodes.edges.EdgeWithWeight;
import projects.maman15.nodes.nodeImplementations.GHSNode;
import sinalgo.configuration.Configuration;
import sinalgo.gui.controlPanel.ControlPanel;
import sinalgo.nodes.edges.Edge;
import sinalgo.runtime.AbstractCustomGlobal;
import sinalgo.runtime.Global;
import sinalgo.runtime.Runtime;
import sinalgo.runtime.SynchronousRuntimeThread;
import sinalgo.tools.Tools;
import sinalgo.tools.statistics.UniformDistribution;

import javax.naming.ldap.Control;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Vector;

/**
 * This class holds customized global state and methods for the framework.
 * The only mandatory method to overwrite is
 * <code>hasTerminated</code>
 * <br>
 * Optional methods to override are
 * <ul>
 * <li><code>customPaint</code></li>
 * <li><code>handleEmptyEventQueue</code></li>
 * <li><code>onExit</code></li>
 * <li><code>preRun</code></li>
 * <li><code>preRound</code></li>
 * <li><code>postRound</code></li>
 * <li><code>checkProjectRequirements</code></li>
 * </ul>
 *
 * @see AbstractCustomGlobal for more details.
 * <br>
 * In addition, this class also provides the possibility to extend the framework with
 * custom methods that can be called either through the menu or via a button that is
 * added to the GUI.
 */
public class CustomGlobal extends AbstractCustomGlobal {
    // A vector of all the nodes
    static Vector<GHSNode> nodes = new Vector<GHSNode>();
    // A uniform distribution between 0 and 1
    UniformDistribution dist = new UniformDistribution(0, 1);
    // The HashMap containing the weights of the edges in the graph
    static HashMap<Integer, HashMap<Integer, Integer>> weights = new HashMap<>();
    // The sum of the weights
    BigInteger sumOfWeights = BigInteger.ZERO;


    public static Integer getWeight(int start, int end) {
        return weights.get(start).get(end);
    }


    public static int getNumOfNodes() {
        return nodes.size();
    }


    public void buildGraph(int numNodes) {
        if (numNodes <= 0) {
            Tools.showMessageDialog("The number of nodes needs to be at least 1.\nCreation of graph aborted.");
            return;
        }

        // Clear all nodes (if any)
        Runtime.clearAllNodes();
        weights.clear();
        nodes.clear();
        sumOfWeights = BigInteger.ZERO;

        // Create the nodes in a random position uniformly selected from [0, dimX] x [0, dimY]
        for (int i = 0; i < numNodes; ++i) {
            GHSNode node = new GHSNode();

            // Initialize the node to a random location in the screen
            double x = dist.nextSample() * Configuration.dimX;
            double y = dist.nextSample() * Configuration.dimY;
            node.setPosition(x, y, 0);

            // Finalized the initialization (Sinalgo stuff)
            node.finishInitializationWithDefaultModels(true);

            // Add the node to the nodes vector
            nodes.add(node);

            // Initialized the hashmap of the weights of the current node outgoing edges
            weights.put(node.ID, new HashMap<>());
        }

        // Choose 7 random nodes from the available nodes to connect to
        for (GHSNode currNode : nodes) {
            // Build a vector containing the nodes which are not connected yet to the current node
            Vector<GHSNode> availableNodes = new Vector<>(nodes);
            availableNodes.remove(currNode);
            for (Edge e : currNode.outgoingConnections) {
                availableNodes.remove(e.endNode);
            }

            // Choose 7 nodes from the available ones and connect to them with a weighted edge
            for (int i = 0; i < 7; ++i) {
                if (availableNodes.isEmpty()) break;

                // Randomly select a node from the available ones and remove it from the available nodes vector
                GHSNode neighbor = availableNodes.get((int) (dist.nextSample() * availableNodes.size()));
                availableNodes.remove(neighbor);

                // Initialize a random weight to the new edge
                int weight = EdgeWithWeight.generateRandomWeight();
                sumOfWeights = sumOfWeights.add(BigInteger.valueOf(weight));
                weights.get(currNode.ID).put(neighbor.ID, weight);
                weights.get(neighbor.ID).put(currNode.ID, weight);

                // Add the connection
                currNode.addConnectionTo(neighbor);
            }
        }

        // Repaint the GUI as we have added some nodes and edges
        Tools.repaintGUI();
    }


    @CustomButton(buttonText = "Build New Graph", toolTipText = "Build a graph with a given amount of nodes")
    public void buildGraph() {
        try {
            String input = Tools.showQueryDialog("Enter the number of nodes in graph:");
            if (input == null) return;
            int numNodes = Integer.parseInt(input);
            buildGraph(numNodes);
            Tools.getNodeSelectedByUser(n -> {
                if (n == null || !(n instanceof GHSNode)) {
                    return; // aborted
                }
                GHSNode node = (GHSNode) n;
                node.setIsServer(true);
                Tools.showMessageDialog("The Node " + node.ID + " selected as Server");
            }, "Select one node to be the server...");
        } catch (NumberFormatException e) {
            Tools.showMessageDialog("Please enter a valid number");
        }
    }
    
    
    @CustomButton(buttonText = "Send message to server", toolTipText = "Send message to server from a chossen Node")
    public void sendMessage() {
    	if (hasFoundMST()) {
    		try {
               
                Tools.getNodeSelectedByUser(n -> {
                    if (n == null || !(n instanceof GHSNode)) {
                        return; // aborted
                    }
                    GHSNode node = (GHSNode) n;
                    String input = Tools.showQueryDialog("Enter the message content:");
                    node.startMessageSending(input);
                    Tools.showMessageDialog("The Node " + node.ID + " send the message: " +input+ " to the Server");
                }, "Select one node to be the sender of the message to the server...");
            } catch (Exception e) {
                Tools.showMessageDialog("Somthing went wrong");
            }
    	}
    	else {
            Tools.showMessageDialog("Didn't find MST yet.");
    	}
        
    }
    

    //Print the current fragments using BFS
    @CustomButton(buttonText = "Print Fragments", toolTipText = "Print a BFS scan of the fragments")
    public void printFragments() {
        PrintStream board = Tools.getTextOutputPrintStream();
        // Find all the roots of the fragments
        Vector<GHSNode> roots = new Vector<>();
        for (GHSNode node : nodes) {
            if (node.getParent() == null) {
                roots.add(node);
            }
        }

        // For every root, use BFS to scan the fragment and print the nodes
        int count = nodes.size();
        Vector<GHSNode> queue = new Vector<>();
        for (GHSNode root : roots) {
            board.println("-------------------------------");
        	board.println("Fragment of " + root.ID);
            queue.add(root);
            while (!queue.isEmpty()) {
                GHSNode curr = queue.remove(0);
                curr.printParent();
                queue.addAll(curr.getChildren());
                if (--count == 0) return;
            }
        }
    }


    @Override
    public boolean hasTerminated() {
        for (GHSNode node : nodes) {
            if (!node.hasFinished()) {
                return false;
            }
        }
        return true;
    }


    public static boolean hasFoundMST() {
        for (GHSNode node : nodes) {
            if (!node.hasFoundMST()) {
                return false;
            }
        }
        return true;
    }


    public BigInteger calculateSumOfWeights() {
        return sumOfWeights;
    }


    public BigInteger calculateSumOfWeightsInMST() {
        if (!hasFoundMST()) return null;
        BigInteger sum = BigInteger.ZERO;
        for (GHSNode node : nodes) {
            for (GHSNode child : node.getChildren()) {
                sum = sum.add(BigInteger.valueOf(getWeight(node.ID, child.ID)));
            }
        }
        return sum;
    }

    /**
     * Show the sum of the weights in the graph.
     */
    @CustomButton(buttonText = "Print sum of weights", toolTipText = "Calculate the sum of the weights in the graph")
    public void showSumOfWeights() {
        PrintStream board = Tools.getTextOutputPrintStream();
        board.println("-------------------------------");
    	board.println("Sum of weights in graph: \n" + calculateSumOfWeights());
    }


    @CustomButton(buttonText = "Print sum of weights in the MST", toolTipText = "Calculate the sum of the weights in the graph")
    public void showSumOfWeightsInMST() {
        PrintStream board = Tools.getTextOutputPrintStream();
        BigInteger sum = calculateSumOfWeightsInMST();
        if (sum == null) Tools.showMessageDialog("MST wasn't found yet.");
        else {
        	board.println("-------------------------------");
        	board.println("Sum of weights in MST: \n" + calculateSumOfWeights());
        }
    }
    
    
    FileWriter resultsFile;

    //Append the number of nodes, the sum of the weights in the graph and the sum of the weights in the MST to the results file
    @CustomButton(buttonText = "Export results to results file", toolTipText = "Export the results to results file (results.csv)")
    public void saveResults() {
        if (!hasFoundMST()) {
            Tools.showMessageDialog("Didn't find MST yet.");
            return;
        }
        try {
            boolean isResultsFileExist = (new File(Paths.get(System.getProperty("user.dir"), "results.csv").toUri())).exists();
            resultsFile = new FileWriter(Paths.get(System.getProperty("user.dir"), "results.csv").toString(), true);
            if (!isResultsFileExist) {
                resultsFile.write("Number of nodes,Sum of weights,Sum of weights in MST\n");
            }
            resultsFile.write(String.format("%d,%d,%d\n", getNumOfNodes(), calculateSumOfWeights(), calculateSumOfWeightsInMST()));
            resultsFile.close();
        } catch (IOException e) {
            Tools.showMessageDialog("Failed writing results.");
        }
    }
}

