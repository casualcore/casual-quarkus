/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.netty.channel.Channel;
import io.quarkiverse.ironjacamar.runtime.endpoint.MessageEndpointWrapper;
import jakarta.resource.spi.XATerminator;
import jakarta.resource.spi.endpoint.MessageEndpoint;
import jakarta.resource.spi.work.WorkManager;
import se.laz.casual.api.network.protocol.messages.CasualNWMessage;
import se.laz.casual.jca.inflow.CasualInboundTransactionRegistry;
import se.laz.casual.jca.inflow.CasualMessageListener;
import se.laz.casual.network.ProtocolVersion;
import se.laz.casual.network.protocol.messages.domain.CasualDomainConnectRequestMessage;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryRequestMessage;
import se.laz.casual.network.protocol.messages.domain.DomainDisconnectReplyMessage;
import se.laz.casual.network.protocol.messages.service.CasualServiceCallRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceCommitRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourcePrepareRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceRollbackRequestMessage;

import java.util.function.Consumer;

public class CasualMessageEndpointWrapper extends MessageEndpointWrapper implements CasualMessageListener
{
    private final CasualMessageListener listener;

    public CasualMessageEndpointWrapper(MessageEndpoint endpoint, CasualMessageListener listener)
    {
        super(endpoint);
        this.listener = listener;
    }

    @Override
    public void domainConnectRequest(CasualNWMessage<CasualDomainConnectRequestMessage> message, Channel channel, Consumer<ProtocolVersion> protocolVersion)
    {
        listener.domainConnectRequest(message, channel, protocolVersion);
    }

    @Override
    public void domainDisconnectReply(CasualNWMessage<DomainDisconnectReplyMessage> message)
    {
        listener.domainDisconnectReply(message);
    }

    @Override
    public void domainDiscoveryRequest(CasualNWMessage<CasualDomainDiscoveryRequestMessage> casualNWMessage, Channel channel, ProtocolVersion protocolVersion)
    {
        listener.domainDiscoveryRequest(casualNWMessage, channel, protocolVersion);
    }

    @Override
    public void serviceCallRequest(CasualNWMessage<CasualServiceCallRequestMessage> casualNWMessage, Channel channel, WorkManager workManager, CasualInboundTransactionRegistry casualInboundTransactionRegistry, ProtocolVersion protocolVersion)
    {
        listener.serviceCallRequest(casualNWMessage, channel, workManager, casualInboundTransactionRegistry, protocolVersion);
    }

    @Override
    public void prepareRequest(CasualNWMessage<CasualTransactionResourcePrepareRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        listener.prepareRequest(message, channel, xaTerminator, inboundTransactionRegistry);
    }

    @Override
    public void commitRequest(CasualNWMessage<CasualTransactionResourceCommitRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        listener.commitRequest(message, channel, xaTerminator, inboundTransactionRegistry);
    }

    @Override
    public void requestRollback(CasualNWMessage<CasualTransactionResourceRollbackRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        listener.requestRollback(message, channel, xaTerminator, inboundTransactionRegistry);
    }
}
