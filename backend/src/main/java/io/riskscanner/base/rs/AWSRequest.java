package io.riskscanner.base.rs;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.gson.Gson;

public class AWSRequest extends Request {
	private AWSCredential awsCredential;
	
	public AWSRequest() {
		super("", "");
	}

	public AWSRequest(Request req) {
		super(req.getCredential(), req.getRegionId());
		setCredential(req.getCredential());
		setRegionId(req.getRegionId());
	}
	
	public String getSecretKey() {
		awsCredential = getAwsCredential();
		if(awsCredential != null) {
			return awsCredential.getSecretKey();
		}
		return null;
	}

	public String getAccessKey() {
		awsCredential = getAwsCredential();
		if(awsCredential != null) {
			return awsCredential.getAccessKey();
		}
		return null;
	}

	public AWSCredential getAwsCredential() {
		if(awsCredential == null) {
			awsCredential = new Gson().fromJson(getCredential(), AWSCredential.class);
		}
		return awsCredential;
	}
	
	public AmazonEC2Client getAmazonEC2Client() {
		if(getAccessKey() != null && getAccessKey().trim().length() > 0 && getSecretKey() != null && getSecretKey().trim().length() > 0) {
			AmazonEC2Client client = new AmazonEC2Client(new BasicAWSCredentials(getAccessKey(), getSecretKey()));
			if(getRegionId() != null && getRegionId().trim().length() > 0) {
				Region r = RegionUtils.getRegion(getRegionId());
				if(r != null) {
					client.setRegion(r);
				}
			}
			return client;
		}
		return null;
	}

}
