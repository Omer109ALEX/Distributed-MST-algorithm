package projects.maman15.nodes.messages;

//The possible types of messages.
public enum MessageType {
	/**
	 * A message that is broadcasted in the MWOE_BROADCASTING state of the GHS algorithm.
	 * This message is used to broadcast the MWOE that was chosen by the root of the fragment to be added to the MST.
	 */
	ChosenMWOEMessage,
	/**
	 * A message that is sent in the NEW_ROOT_BROADCASTING state of the GHS algorithm.
	 * This message indicates that there's a new root to the fragment, and is sent on the route from the new root to the old one to flip the edges' direction.
	 */
	FlipEdgeDirectionMessage, 
	/**
	 * A message that is sent in the FRAGMENT_ID_DISCOVERY state of the GHS algorithm.
	 * This message indicates that the neighbor has changed its fragment ID.
	 */
	FragmentIDMessage,
	/**
	 * A message that is broadcasted in the FRAGMENT_ID_DISCOVERY state of the GHS algorithm.
	 * This message is used by the root to broadcast its ID as the new fragment ID.
	 */
	FragmentIDUpdateMessage,
	/**
	 * A message that is sent in the MWOE_SEND state of the GHS algorithm.
	 * This message indicates that the sender is connecting its fragment to the receiver's fragment using the edge between them.
	 */
	MWOEChoiceMessage,
	/**
	 * A message that is sent in the MWOE_SEARCHING state of the GHS algorithm.
	 * This message is used to ConvergeCast the MWOEs to the root of the fragment.
	 * Each node sends this message to its parent, after he received this message from all of its children and took the minimal MWOE from them.
	 * This message also contains the number of nodes in the subtree of the current node, which is updated in every node according to its children and used to let the root know the number of nodes in its fragment.
	 */
	MWOESuggestionMessage,
	/**
	 * A message that is broadcasted when the algorithm is finished, i.e. when the root finds out all the nodes in the graph are in its fragment.
	 * This message starts the server reRouting processes which makes the server the root of the MST.
	 */
	StartServerReroutingMessage, 
}
