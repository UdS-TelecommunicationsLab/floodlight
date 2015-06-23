package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;

import org.projectfloodlight.openflow.types.IPv4Address;

/**
 * The Class IGMP representing an IGMP packet (currently, only IGMPv3 is
 * supported). See RFC 3376 for more information
 * 
 * @author Tobias Theobald
 */
public class IGMP extends BasePacket {

	public static final byte TYPE_IGMP_V1_MEMBERSHIP_REPORT = 0x12;
	public static final byte TYPE_IGMP_V2_MEMBERSHIP_REPORT = 0x16;
	public static final byte TYPE_IGMP_V2_LEAVE_GROUP = 0x17;

	public static final byte TYPE_IGMP_V3_MEMBERSHIP_QUERY = 0x11;
	public static final byte TYPE_IGMP_V3_MEMBERSHIP_REPORT = 0x22;

	private byte type;
	private short checksum;
	private byte[] additionalData = new byte[0];

	// Only for Membership Queries
	private byte maxRespCode;
	private int groupAddress;
	private boolean suppressRouterSideProcessing;
	private byte queriersRobustnessVariable;
	private byte queriersQueryIntervalCode;
	private short numberOfSources;
	private int[] sourceAddresses;

	// Only for membership reports
	private short numberOfGroupRecords;
	private IGMPv3GroupRecord[] groupRecords;

	@Override
	public byte[] serialize() {
		ByteBuffer buffer;
		byte[] retVal;
		boolean haveToComputeChecksum = checksum == 0;
		switch (type) {
		case TYPE_IGMP_V3_MEMBERSHIP_QUERY:
			// sanity check source address length
			if (numberOfSources == 0) {
				numberOfSources = (short) sourceAddresses.length;
			} else if (numberOfSources != sourceAddresses.length) {
				throw new IllegalStateException(
						"Number of source addresses and source address array "
								+ "length are not the same");
			}
			buffer = ByteBuffer.allocate(12 + numberOfSources * 4
					+ additionalData.length);
			buffer.put(type);
			buffer.put(maxRespCode);
			buffer.putShort(checksum);
			buffer.putInt(groupAddress);
			buffer.put((byte) ((suppressRouterSideProcessing ? 1 : 0) << 3 | queriersRobustnessVariable & 0b111));
			buffer.put(queriersQueryIntervalCode);
			buffer.putShort(numberOfSources);
			for (int sourceAddress : sourceAddresses)
				buffer.putInt(sourceAddress);
			buffer.put(additionalData);
			retVal = buffer.array();
			break;
		case TYPE_IGMP_V3_MEMBERSHIP_REPORT:
			// sanity check group record length
			if (numberOfGroupRecords == 0) {
				numberOfGroupRecords = (short) groupRecords.length;
			} else if (numberOfGroupRecords != groupRecords.length) {
				throw new IllegalStateException(
						"Number of group records and group record array "
								+ "length are not the same");
			}

			// Compute total length
			byte[][] serializedGroupRecords = new byte[numberOfGroupRecords][];
			int packetLength = 8 + additionalData.length;
			for (int i = 0; i < numberOfGroupRecords; i++) {
				serializedGroupRecords[i] = groupRecords[i].serialize();
				packetLength += serializedGroupRecords[i].length;
			}

			buffer = ByteBuffer.allocate(packetLength);
			buffer.put(type);
			buffer.put((byte) 0);
			buffer.putShort(checksum);
			buffer.putShort((short) 0);
			buffer.putShort(numberOfGroupRecords);
			for (byte[] record : serializedGroupRecords)
				buffer.put(record);
			buffer.put(additionalData);
			retVal = buffer.array();
			break;
		case TYPE_IGMP_V1_MEMBERSHIP_REPORT:
		case TYPE_IGMP_V2_MEMBERSHIP_REPORT:
		case TYPE_IGMP_V2_LEAVE_GROUP:
		default:
			// not supported
			retVal = null;
			break;
		}
		if (haveToComputeChecksum && retVal != null) {
			checksum = calculateChecksum(retVal);
			retVal[2] = (byte) ((checksum >> 8) & 0xff);
			retVal[3] = (byte) (checksum & 0xff);
		}
		return retVal;
	}

	@Override
	public IPacket deserialize(byte[] data, int offset, int length)
			throws PacketParsingException {
		ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
		type = bb.get();
		// TODO include checksum computation and checking
		switch (type) {
		case TYPE_IGMP_V1_MEMBERSHIP_REPORT:
		case TYPE_IGMP_V2_LEAVE_GROUP:
		case TYPE_IGMP_V2_MEMBERSHIP_REPORT:
			// not supported
			break;
		case TYPE_IGMP_V3_MEMBERSHIP_QUERY:
			maxRespCode = bb.get();
			checksum = bb.getShort();
			groupAddress = bb.getInt();
			byte flags = bb.get();
			suppressRouterSideProcessing = (flags & 0b1000) > 0;
			queriersRobustnessVariable = (byte) (flags & 0b0111);
			queriersQueryIntervalCode = bb.get();
			numberOfSources = bb.getShort();
			sourceAddresses = new int[numberOfSources];
			for (int i = 0; i < numberOfSources; i++) {
				sourceAddresses[i] = bb.getInt();
			}
			additionalData = new byte[bb.remaining()];
			if (bb.remaining() != 0) {
				bb.get(additionalData);
			}
			break;
		case TYPE_IGMP_V3_MEMBERSHIP_REPORT:
			bb.get();
			checksum = bb.getShort();
			bb.getShort();
			numberOfGroupRecords = bb.getShort();
			groupRecords = new IGMPv3GroupRecord[numberOfGroupRecords];
			for (int i = 0; i < numberOfGroupRecords; i++) {
				groupRecords[i] = new IGMPv3GroupRecord();
				groupRecords[i].deserialize(bb);
			}
			additionalData = new byte[bb.remaining()];
			if (bb.remaining() != 0) {
				bb.get(additionalData);
			}
			break;
		}
		return this;
	}

	public byte getType() {
		return type;
	}

	public IGMP setType(byte type) {
		this.type = type;
		return this;
	}

	public boolean isSupportedMessage() {
		return isIGMPv3MembershipQueryMessage()
				|| isIGMPv3MembershipReportMessage();
	}

	public boolean isIGMPv3MembershipReportMessage() {
		return type == TYPE_IGMP_V3_MEMBERSHIP_REPORT;
	}

	public boolean isIGMPv3MembershipQueryMessage() {
		return type == TYPE_IGMP_V3_MEMBERSHIP_QUERY;
	}

	public short getChecksum() {
		return checksum;
	}

	public IGMP setChecksum(short checksum) {
		this.checksum = checksum;
		return this;
	}

	public byte[] getAdditionalData() {
		return additionalData;
	}

	public IGMP setAdditionalData(byte[] additionalData) {
		this.additionalData = additionalData;
		return this;
	}

	public byte getMaxRespCode() {
		return maxRespCode;
	}

	public IGMP setMaxRespCode(byte maxRespCode) {
		this.maxRespCode = maxRespCode;
		return this;
	}

	public int getGroupAddress() {
		return groupAddress;
	}

	public IGMP setGroupAddress(int groupAddress) {
		this.groupAddress = groupAddress;
		return this;
	}

	public boolean isSuppressRouterSideProcessing() {
		return suppressRouterSideProcessing;
	}

	public IGMP setSuppressRouterSideProcessing(
			boolean suppressRouterSideProcessing) {
		this.suppressRouterSideProcessing = suppressRouterSideProcessing;
		return this;
	}

	public byte getQueriersRobustnessVariable() {
		return queriersRobustnessVariable;
	}

	public IGMP setQueriersRobustnessVariable(byte queriersRobustnessVariable) {
		this.queriersRobustnessVariable = queriersRobustnessVariable;
		return this;
	}

	public byte getQueriersQueryIntervalCode() {
		return queriersQueryIntervalCode;
	}

	public IGMP setQueriersQueryIntervalCode(byte queriersQueryIntervalCode) {
		this.queriersQueryIntervalCode = queriersQueryIntervalCode;
		return this;
	}

	public short getNumberOfSources() {
		return numberOfSources;
	}

	public IGMP setNumberOfSources(short numberOfSources) {
		this.numberOfSources = numberOfSources;
		return this;
	}

	public int[] getSourceAddresses() {
		return sourceAddresses;
	}

	public IGMP setSourceAddresses(int[] sourceAddresses) {
		this.sourceAddresses = sourceAddresses;
		return this;
	}

	public short getNumberOfGroupRecords() {
		return numberOfGroupRecords;
	}

	public IGMP setNumberOfGroupRecords(short numberOfGroupRecords) {
		this.numberOfGroupRecords = numberOfGroupRecords;
		return this;
	}

	public IGMPv3GroupRecord[] getGroupRecords() {
		return groupRecords;
	}

	public IGMP setGroupRecords(IGMPv3GroupRecord[] groupRecords) {
		this.groupRecords = groupRecords;
		return this;
	}

	public static class IGMPv3GroupRecord {

		public static final byte RECORD_TYPE_MODE_IS_INCLUDE = 0x01;
		public static final byte RECORD_TYPE_MODE_IS_EXCLUDE = 0x02;
		public static final byte RECORD_TYPE_CHANGE_TO_INCLUDE_MODE = 0x03;
		public static final byte RECORD_TYPE_CHANGE_TO_EXCLUDE_MODE = 0x04;
		public static final byte RECORD_TYPE_ALLOW_NEW_SOURCES = 0x05;
		public static final byte RECORD_TYPE_BLOCK_OLD_SOURCES = 0x06;

		private byte recordType;
		private byte auxiliaryDataLength; // number of INTs!
		private short numberOfSources;
		private IPv4Address multicastAddress;
		private IPv4Address[] sourceAddresses;
		private int[] auxiliaryData;

		public byte[] serialize() {
			ByteBuffer bb = ByteBuffer.allocate(8 + numberOfSources * 4
					+ auxiliaryDataLength * 4);

			bb.put(recordType);
			bb.put(auxiliaryDataLength);
			bb.putShort(numberOfSources);
			bb.putInt(multicastAddress.getInt());

			for (IPv4Address sourceAddress : sourceAddresses)
				bb.putInt(sourceAddress.getInt());

			for (int auxiliaryDatum : auxiliaryData)
				bb.putInt(auxiliaryDatum);

			return bb.array();
		}

		/**
		 * Deserializes a Group Record
		 * 
		 * @param data
		 *            array containing the packet
		 * @param offset
		 *            offset at which to start
		 * @return number of bytes digested
		 */
		public IGMPv3GroupRecord deserialize(ByteBuffer bb) {
			recordType = bb.get();
			auxiliaryDataLength = bb.get();
			numberOfSources = bb.getShort();
			multicastAddress = IPv4Address.of(bb.getInt());

			sourceAddresses = new IPv4Address[numberOfSources];
			for (int i = 0; i < numberOfSources; i++) {
				sourceAddresses[i] = IPv4Address.of(bb.getInt());
			}

			auxiliaryData = new int[auxiliaryDataLength];
			for (int i = 0; i < auxiliaryDataLength; i++) {
				auxiliaryData[i] = bb.getInt();
			}

			return this;
		}

		public byte getRecordType() {
			return recordType;
		}

		public IGMPv3GroupRecord setRecordType(byte recordType) {
			this.recordType = recordType;
			return this;
		}

		public byte getAuxiliaryDataLength() {
			return auxiliaryDataLength;
		}

		public IGMPv3GroupRecord setAuxiliaryDataLength(byte auxiliaryDataLength) {
			this.auxiliaryDataLength = auxiliaryDataLength;
			return this;
		}

		public short getNumberOfSources() {
			return numberOfSources;
		}

		public IGMPv3GroupRecord setNumberOfSources(short numberOfSources) {
			this.numberOfSources = numberOfSources;
			return this;
		}

		public IPv4Address getMulticastAddress() {
			return multicastAddress;
		}

		public IGMPv3GroupRecord setMulticastAddress(IPv4Address multicastAddress) {
			this.multicastAddress = multicastAddress;
			return this;
		}

		public IPv4Address[] getSourceAddresses() {
			return sourceAddresses;
		}

		public IGMPv3GroupRecord setSourceAddresses(IPv4Address[] sourceAddresses) {
			this.sourceAddresses = sourceAddresses;
			return this;
		}

		public int[] getAuxiliaryData() {
			return auxiliaryData;
		}

		public IGMPv3GroupRecord setAuxiliaryData(int[] auxiliaryData) {
			this.auxiliaryData = auxiliaryData;
			return this;
		}
	}

	/**
	 * Calculate the Internet Checksum of a buffer (RFC 1071 -
	 * http://www.faqs.org/rfcs/rfc1071.html) Algorithm is 1) apply a 16-bit 1's
	 * complement sum over all octets (adjacent 8-bit pairs [A,B], final odd
	 * length is [A,0]) 2) apply 1's complement to this final sum
	 *
	 * Notes: 1's complement is bitwise NOT of positive value. Ensure that any
	 * carry bits are added back to avoid off-by-one errors
	 *
	 * @author Gary Rowe - StackOverflow.com
	 *
	 * @param buf
	 *            The message
	 * @return The checksum
	 * 
	 */
	public static short calculateChecksum(byte[] buf) {
		int length = buf.length;
		int i = 0;

		long sum = 0;
		long data;

		// Handle all pairs
		while (length > 1) {
			// Corrected to include @Andy's edits and various comments on Stack
			// Overflow
			data = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
			sum += data;
			// 1's complement carry bit correction in 16-bits (detecting sign
			// extension)
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}

			i += 2;
			length -= 2;
		}

		// Handle remaining byte in odd length buffers
		if (length > 0) {
			// Corrected to include @Andy's edits and various comments on Stack
			// Overflow
			sum += (buf[i] << 8 & 0xFF00);
			// 1's complement carry bit correction in 16-bits (detecting sign
			// extension)
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}
		}

		// Final 1's complement value correction to 16-bits
		sum = ~sum;
		sum = sum & 0xFFFF;
		return (short) sum;

	}

}
