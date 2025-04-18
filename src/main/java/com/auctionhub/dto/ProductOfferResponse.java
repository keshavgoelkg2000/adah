package com.auctionhub.dto;

import java.util.ArrayList;
import java.util.List;

import com.auctionhub.entity.ProductOffer;

import lombok.Data;

@Data
public class ProductOfferResponse extends CommonApiResponse {
	
	List<ProductOffer> offers = new ArrayList<>();

}
