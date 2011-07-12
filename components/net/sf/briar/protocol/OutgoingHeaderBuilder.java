package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.HashSet;
import java.util.Set;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.serial.WriterFactory;

public class OutgoingHeaderBuilder extends HeaderBuilderImpl {

	OutgoingHeaderBuilder(KeyPair keyPair, Signature signature,
			MessageDigest messageDigest, WriterFactory writerFactory) {
		super(keyPair, signature, messageDigest, writerFactory);
	}

	public void setSignature(byte[] sig) {
		throw new UnsupportedOperationException();
	}

	public Header build() throws IOException, GeneralSecurityException {
		byte[] raw = getSignableRepresentation();
		signature.initSign(keyPair.getPrivate());
		signature.update(raw);
		byte[] sig = signature.sign();
		messageDigest.reset();
		messageDigest.update(raw);
		messageDigest.update(sig);
		byte[] hash = messageDigest.digest();
		Set<BatchId> ackSet = new HashSet<BatchId>(acks);
		Set<GroupId> subSet = new HashSet<GroupId>(subs);
		return new HeaderImpl(new BundleId(hash), raw.length, ackSet, subSet,
				transports, sig);
	}
}
