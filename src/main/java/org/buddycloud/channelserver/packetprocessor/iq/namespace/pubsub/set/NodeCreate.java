package org.buddycloud.channelserver.packetprocessor.iq.namespace.pubsub.set;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.buddycloud.channelserver.channel.node.configuration.NodeConfigurationException;
import org.buddycloud.channelserver.db.DataStore;
import org.buddycloud.channelserver.db.DataStoreException;
import org.buddycloud.channelserver.packetprocessor.iq.namespace.pubsub.JabberPubsub;
import org.buddycloud.channelserver.packetprocessor.iq.namespace.pubsub.PubSubElementProcessorAbstract;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.dom.DOMElement;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

public class NodeCreate extends PubSubElementProcessorAbstract
{
	private static final Pattern nodeExtract = Pattern.compile("^/user/[^@]+@([^/]+)/[^/]+$");
    private static final String NODE_REG_EX  = "^/user/[^@]+@[^/]+/[^/]+$";
	private static final String INVALID_NODE_CONFIGURATION = "Invalid node configuration";
	
	public NodeCreate(BlockingQueue<Packet> outQueue, DataStore dataStore)
    {
    	setDataStore(dataStore);
    	setOutQueue(outQueue);
    }

	public void process(Element elm, JID actorJID, IQ reqIQ, Element rsm) 
	    throws Exception
    {
    	element     = elm;
    	response    = IQ.createResultIQ(reqIQ);
    	request     = reqIQ;
    	actor       = actorJID;
        node        = element.attributeValue("node");
        
        if (null == actorJID) {
        	actor = request.getFrom();
        }
    	if ((false == validateNode()) 
    	    || (true == doesNodeExist())
    	    || (false == actorIsRegistered())
    	    || (false == nodeHandledByThisServer())
    	) {
            outQueue.put(response);
    		return;
    	}
    	createNode();
    }

	private void createNode() throws InterruptedException
	{
		try {
		    dataStore.createNode(
		        actor.toString(),
		        node,
		        getNodeConfiguration()
		    );
		} catch (DataStoreException e) {
			setErrorCondition(
			    PacketError.Type.wait,
			    PacketError.Condition.internal_server_error
			);
			outQueue.put(response);
			return;
		} catch (NodeConfigurationException e) {
			setErrorCondition(
			    PacketError.Type.modify,
			    PacketError.Condition.bad_request
		    );
			outQueue.put(response);
			return;
		}
		response.setType(IQ.Type.result);
		outQueue.put(response);
	}

	public boolean accept(Element elm)
	{
		return elm.getName().equals("create");
	}
	
	private HashMap<String, String> getNodeConfiguration()
	{
		getNodeConfigurationHelper().parse(request);
		if (false == getNodeConfigurationHelper().isValid()) {
			throw new NodeConfigurationException(INVALID_NODE_CONFIGURATION);
		}
		return getNodeConfigurationHelper().getValues();
	}

	private boolean validateNode()
	{
        if (node != null && !node.trim().equals("")) {
        	return true;
        }
    	response.setType(IQ.Type.error);
    	Element nodeIdRequired = new DOMElement(
            "nodeid-required",
            new Namespace("", JabberPubsub.NS_PUBSUB_ERROR)
        );
    	Element badRequest = new DOMElement(
    	    PacketError.Condition.bad_request.toString(),
            new Namespace("", JabberPubsub.NS_XMPP_STANZAS)
    	);
        Element error = new DOMElement("error");
        error.addAttribute("type", "modify");
        error.add(badRequest);
        error.add(nodeIdRequired);
        response.setChildElement(error);
        return false;
	}
	
	private boolean doesNodeExist() throws DataStoreException
	{
		if (false == dataStore.nodeExists(node)) {
			return false;
		}
		setErrorCondition(
			PacketError.Type.cancel,
		    PacketError.Condition.conflict
		);
		return true;
	}
	
	private boolean actorIsRegistered()
	{
		if (true == actor.getDomain().equals(getServerDomain())) {
			return true;
		}
		setErrorCondition(
			PacketError.Type.auth,
		    PacketError.Condition.forbidden
		);
		return false;
	}

	private boolean nodeHandledByThisServer()
	{
		if (false == node.matches(NODE_REG_EX)) {
			setErrorCondition(
				PacketError.Type.modify,
			    PacketError.Condition.bad_request
			);
			return false;
		}

		if ((false == node.contains("@" + getServerDomain())) 
		    && (false == node.contains("@" + getTopicsDomain()))
		) {
			setErrorCondition(
			    PacketError.Type.modify,
			    PacketError.Condition.not_acceptable
			);
			return false;
		}
		return true;
	}
}