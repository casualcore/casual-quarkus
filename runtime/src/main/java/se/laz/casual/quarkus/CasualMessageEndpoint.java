/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import io.netty.channel.Channel;
import io.quarkiverse.ironjacamar.ResourceEndpoint;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.resource.spi.XATerminator;
import jakarta.resource.spi.work.WorkManager;
import se.laz.casual.api.network.protocol.messages.CasualNWMessage;
import se.laz.casual.jca.inflow.CasualInboundTransactionRegistry;
import se.laz.casual.jca.inflow.CasualMessageListener;
import se.laz.casual.jca.inflow.CasualMessageListenerImpl;
import se.laz.casual.network.ProtocolVersion;
import se.laz.casual.network.protocol.messages.domain.CasualDomainConnectRequestMessage;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryRequestMessage;
import se.laz.casual.network.protocol.messages.domain.DomainDisconnectReplyMessage;
import se.laz.casual.network.protocol.messages.service.CasualServiceCallRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceCommitRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourcePrepareRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceRollbackRequestMessage;

import java.lang.System.Logger;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.DEBUG;

@ApplicationScoped
@ResourceEndpoint
@Identifier("casual")
public class CasualMessageEndpoint implements CasualMessageListener
{
    private static final Logger LOG = System.getLogger(CasualMessageEndpoint.class.getName());
    private final CasualMessageListenerImpl delegate = new CasualMessageListenerImpl();

    @Override
    public void domainConnectRequest(CasualNWMessage<CasualDomainConnectRequestMessage> message, Channel channel, Consumer<ProtocolVersion> protocolVersion)
    {
        LOG.log(DEBUG, () -> "Received domain connect request" + message);
        delegate.domainConnectRequest(message, channel, protocolVersion);
    }

    @Override
    public void domainDisconnectReply(CasualNWMessage<DomainDisconnectReplyMessage> message)
    {
        LOG.log(DEBUG, () -> "Received domain disconnect reply" + message);
        delegate.domainDisconnectReply(message);
    }

    @Override
    public void domainDiscoveryRequest(CasualNWMessage<CasualDomainDiscoveryRequestMessage> message, Channel channel, ProtocolVersion protocolVersion)
    {
        LOG.log(DEBUG, () -> "Received domain discovery request" + message);
        delegate.domainDiscoveryRequest(message, channel, protocolVersion);
    }

    @Override
    public void serviceCallRequest(CasualNWMessage<CasualServiceCallRequestMessage> message, Channel channel, WorkManager workManager, CasualInboundTransactionRegistry inboundTransactionRegistry, ProtocolVersion protocolVersion)
    {
        LOG.log(DEBUG, () -> "Received service call request" + message);
        delegate.serviceCallRequest(message, channel, workManager, inboundTransactionRegistry, protocolVersion);
    }

    @Override
    public void prepareRequest(CasualNWMessage<CasualTransactionResourcePrepareRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        LOG.log(DEBUG, () -> "Received prepare request" + message);
        delegate.prepareRequest(message, channel, xaTerminator, inboundTransactionRegistry);
    }

    @Override
    public void commitRequest(CasualNWMessage<CasualTransactionResourceCommitRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        LOG.log(DEBUG, () -> "Received commit request" + message);
        delegate.commitRequest(message, channel, xaTerminator, inboundTransactionRegistry);
    }

    @Override
    public void requestRollback(CasualNWMessage<CasualTransactionResourceRollbackRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        LOG.log(DEBUG, () -> "Received rollback request" + message);
        delegate.requestRollback(message, channel, xaTerminator, inboundTransactionRegistry);
    }
}
