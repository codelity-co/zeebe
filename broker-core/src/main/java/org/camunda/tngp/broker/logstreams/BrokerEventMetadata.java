package org.camunda.tngp.broker.logstreams;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.clientapi.BrokerEventMetadataDecoder;
import org.camunda.tngp.protocol.clientapi.BrokerEventMetadataEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class BrokerEventMetadata implements BufferWriter, BufferReader
{
    public static final int ENCODED_LENGTH = MessageHeaderEncoder.ENCODED_LENGTH +
            BrokerEventMetadataEncoder.BLOCK_LENGTH;

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    protected BrokerEventMetadataEncoder encoder = new BrokerEventMetadataEncoder();
    protected BrokerEventMetadataDecoder decoder = new BrokerEventMetadataDecoder();

    protected int reqChannelId;
    protected long reqConnectionId;
    protected long reqRequestId;
    protected int raftTermId;

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        reset();

        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        reqChannelId = (int) decoder.reqChannelId();
        reqConnectionId = decoder.reqConnectionId();
        reqRequestId = decoder.reqRequestId();
        raftTermId = (int) decoder.raftTermId();
    }

    @Override
    public int getLength()
    {
        return ENCODED_LENGTH;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset);

        headerEncoder.blockLength(encoder.sbeBlockLength())
            .templateId(encoder.sbeTemplateId())
            .schemaId(encoder.sbeSchemaId())
            .version(encoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        encoder.wrap(buffer, offset);

        encoder.reqChannelId(reqChannelId)
            .reqConnectionId(reqConnectionId)
            .reqRequestId(reqRequestId)
            .raftTermId(raftTermId);
    }

    public int getReqChannelId()
    {
        return reqChannelId;
    }

    public BrokerEventMetadata reqChannelId(int reqChannelId)
    {
        this.reqChannelId = reqChannelId;
        return this;
    }

    public long getReqConnectionId()
    {
        return reqConnectionId;
    }

    public BrokerEventMetadata reqConnectionId(long reqConnectionId)
    {
        this.reqConnectionId = reqConnectionId;
        return this;
    }

    public long getReqRequestId()
    {
        return reqRequestId;
    }

    public BrokerEventMetadata reqRequestId(long reqRequestId)
    {
        this.reqRequestId = reqRequestId;
        return this;
    }

    public int getRaftTermId()
    {
        return raftTermId;
    }

    public BrokerEventMetadata raftTermId(int raftTermId)
    {
        this.raftTermId = raftTermId;
        return this;
    }

    public BrokerEventMetadata reset()
    {
        reqChannelId = (int) BrokerEventMetadataEncoder.reqChannelIdNullValue();
        reqConnectionId = BrokerEventMetadataEncoder.reqConnectionIdNullValue();
        reqRequestId = BrokerEventMetadataDecoder.reqRequestIdNullValue();
        raftTermId = (int) BrokerEventMetadataDecoder.raftTermIdNullValue();
        return this;
    }

    public boolean hasRequestMetadata()
    {
        return reqChannelId != BrokerEventMetadataEncoder.reqChannelIdNullValue() &&
                reqConnectionId != BrokerEventMetadataEncoder.reqConnectionIdNullValue() &&
                reqRequestId != BrokerEventMetadataDecoder.reqRequestIdNullValue();
    }
}
