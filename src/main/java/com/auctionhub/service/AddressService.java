package com.auctionhub.service;

import java.util.List;

import com.auctionhub.entity.Address;
import com.auctionhub.entity.User;

public interface AddressService {
	
	Address addAddress(Address address);
	
	Address updateAddress(Address address);
	
	Address getAddressById(int addressId);

}
