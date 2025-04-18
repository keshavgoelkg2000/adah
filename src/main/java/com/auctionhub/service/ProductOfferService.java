package com.auctionhub.service;

import java.util.List;

import com.auctionhub.entity.Product;
import com.auctionhub.entity.ProductOffer;
import com.auctionhub.entity.User;

public interface ProductOfferService {
	
	ProductOffer addOffer(ProductOffer offer);
	
	ProductOffer updateOffer(ProductOffer offer);
	
	ProductOffer getOfferById(int offerId);
	
	List<ProductOffer> getOffersByUserAndStatus(User user, List<String> status);
	
	List<ProductOffer> getOffersByProductAndStatus(Product product, List<String> status);
	

}
