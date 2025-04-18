package com.auctionhub.scheduler;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.auctionhub.entity.Orders;
import com.auctionhub.entity.Product;
import com.auctionhub.entity.ProductOffer;
import com.auctionhub.entity.User;
import com.auctionhub.service.OrderService;
import com.auctionhub.service.ProductOfferService;
import com.auctionhub.service.ProductService;
import com.auctionhub.service.UserService;
import com.auctionhub.utility.Helper;
import com.auctionhub.utility.Constants.DeliveryStatus;
import com.auctionhub.utility.Constants.DeliveryTime;
import com.auctionhub.utility.Constants.ProductOfferStatus;
import com.auctionhub.utility.Constants.ProductStatus;

@Component
@EnableScheduling
public class UpdateProductOrderTask {
	
	private final Logger LOG = LoggerFactory.getLogger(UpdateProductOrderTask.class);

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductOfferService productOfferService;
	
	@Autowired
	private OrderService orderService;
	
	@Autowired
	private UserService userService;

	@Scheduled(fixedRate = 60000) // 100,000 milliseconds = 5 minutes
	public void updateProductOffers() {
		
		LOG.info("PRODUCT ORDER UPDATE TASK START");

		LocalDateTime now = LocalDateTime.now();

		LocalDateTime todayMidnight = LocalDateTime.of(now.toLocalDate(), LocalTime.MIDNIGHT);

		String startTime = String.valueOf(todayMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

		String endTime = String.valueOf(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

		List<Product> products = this.productService.findByStatusInAndEndDateBetween(
				Arrays.asList(ProductStatus.AVAILABLE.value()), startTime, endTime);
		
		if(CollectionUtils.isEmpty(products)) {
			LOG.info("No available products found");
			LOG.info("PRODUCT ORDER UPDATE TASK END");
			return;
		}

		for (Product product : products) {

			List<ProductOffer> offers = this.productOfferService.getOffersByProductAndStatus(product, Arrays.asList(ProductOfferStatus.ACTIVE.value()));

			if (CollectionUtils.isEmpty(offers)) {

				product.setStatus(ProductStatus.UNSOLD.value());
				productService.updateProduct(product);

				continue;
			}

			// Find the largest amount using Java Stream
			Optional<ProductOffer> maxOffer = offers.stream()
					.max((offer1, offer2) -> offer1.getAmount().compareTo(offer2.getAmount()));

			ProductOffer biggestOffer = maxOffer.get();

			// updating product
			product.setStatus(ProductStatus.SOLD.value());
			product.setBuyer(maxOffer.get().getUser());
			productService.updateProduct(product);
			
			LOG.info("Product Updated");

			for (ProductOffer offer : offers) {

				if (offer == biggestOffer) {
					// updating product offer entry
					offer.setStatus(ProductOfferStatus.WIN.value());
					productOfferService.updateOffer(offer);
				}

				else {
					// updating product offer entry
					offer.setStatus(ProductOfferStatus.LOSE.value());
					productOfferService.updateOffer(offer);
				}
			}
			LOG.info("Product Offer Updated");
			
			// adding the order for the biggest offer
			String orderId = Helper.generateOrderId();
			
			LocalDateTime currentDateTime = LocalDateTime.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
			String formatDateTime = currentDateTime.format(formatter);
			
			Orders order = new Orders();
			order.setOrderId(orderId);
			order.setUser(biggestOffer.getUser());
			order.setProduct(product);
			order.setQuantity(product.getQuantity());
			order.setOrderTime(formatDateTime);
			order.setDeliveryDate(DeliveryStatus.PENDING.value());
			order.setDeliveryStatus(DeliveryStatus.PENDING.value());
			order.setDeliveryTime(DeliveryTime.DEFAULT.value());
			order.setProductOffer(biggestOffer);
			order.setStatus(DeliveryStatus.PENDING.value());

			orderService.addOrder(Arrays.asList(order));
			
			User customer = biggestOffer.getUser();
			customer.setWalletAmount(customer.getWalletAmount().subtract(biggestOffer.getAmount()));
			
			this.userService.updateUser(customer);
			
			User seller = product.getSeller();
			seller.setWalletAmount(seller.getWalletAmount().add(biggestOffer.getAmount()));
			
			this.userService.updateUser(seller);
		
			System.out.println("Order Updated");

		}
		
		System.out.println("PRODUCT ORDER UPDATE TASK END");

	}

}
