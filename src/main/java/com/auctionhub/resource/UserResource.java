package com.auctionhub.resource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.auctionhub.dto.CommonApiResponse;
import com.auctionhub.dto.RegisterUserRequestDto;
import com.auctionhub.dto.UserDto;
import com.auctionhub.dto.UserLoginRequest;
import com.auctionhub.dto.UserLoginResponse;
import com.auctionhub.dto.UserResponseDto;
import com.auctionhub.dto.UserStatusUpdateRequestDto;
import com.auctionhub.entity.Address;
import com.auctionhub.entity.Product;
import com.auctionhub.entity.User;
import com.auctionhub.exception.UserSaveFailedException;
import com.auctionhub.service.AddressService;
import com.auctionhub.service.ProductService;
import com.auctionhub.service.UserService;
import com.auctionhub.utility.JwtUtils;
import com.auctionhub.utility.Constants.ProductStatus;
import com.auctionhub.utility.Constants.UserRole;
import com.auctionhub.utility.Constants.UserStatus;

import jakarta.transaction.Transactional;

@Component
@Transactional
public class UserResource {

	private final Logger LOG = LoggerFactory.getLogger(UserResource.class);

	@Autowired
	private UserService userService;

	@Autowired
	private AddressService addressService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private ProductService productService;

	@Autowired
	private JwtUtils jwtUtils;

	public ResponseEntity<CommonApiResponse> registerAdmin(RegisterUserRequestDto registerRequest) {

		LOG.info("Request received for Register Admin");

		CommonApiResponse response = new CommonApiResponse();

		if (registerRequest == null) {
			response.setResponseMessage("user is null");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		if (registerRequest.getEmailId() == null || registerRequest.getPassword() == null) {
			response.setResponseMessage("missing input");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User existingUser = this.userService.getUserByEmailAndStatus(registerRequest.getEmailId(),
				UserStatus.ACTIVE.value());

		if (existingUser != null) {
			response.setResponseMessage("User already register with this Email");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User user = RegisterUserRequestDto.toUserEntity(registerRequest);

		user.setRole(UserRole.ROLE_ADMIN.value());
		user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
		user.setStatus(UserStatus.ACTIVE.value());

		existingUser = this.userService.addUser(user);

		if (existingUser == null) {
			response.setResponseMessage("failed to register admin");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		response.setResponseMessage("Admin registered Successfully");
		response.setSuccess(true);

		LOG.info("Response Sent!!!");

		return new ResponseEntity<CommonApiResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<CommonApiResponse> registerUser(RegisterUserRequestDto request) {

		LOG.info("Received request for register user");

		CommonApiResponse response = new CommonApiResponse();

		if (request == null) {
			response.setResponseMessage("user is null");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User existingUser = this.userService.getUserByEmailAndStatus(request.getEmailId(), UserStatus.ACTIVE.value());

		if (existingUser != null) {
			response.setResponseMessage("User with this Email Id already resgistered!!!");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		if (request.getRole() == null) {
			response.setResponseMessage("bad request ,Role is missing");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User user = RegisterUserRequestDto.toUserEntity(request);

		String encodedPassword = passwordEncoder.encode(user.getPassword());

		user.setWalletAmount(BigDecimal.ZERO);
		user.setStatus(UserStatus.ACTIVE.value());
		user.setPassword(encodedPassword);

		// delivery person is for seller, so we need to set Seller
		if (user.getRole().equals(UserRole.ROLE_DELIVERY.value())) {

			User seller = this.userService.getUserById(request.getSellerId());

			if (seller == null) {
				response.setResponseMessage("Seller not found,");
				response.setSuccess(false);

				return new ResponseEntity<CommonApiResponse>(response, HttpStatus.INTERNAL_SERVER_ERROR);
			}

			user.setSeller(seller);

		}

		Address address = new Address();
		address.setCity(request.getCity());
		address.setPincode(request.getPincode());
		address.setStreet(request.getStreet());

		Address savedAddress = this.addressService.addAddress(address);

		if (savedAddress == null) {
			throw new UserSaveFailedException("Registration Failed because of Technical issue:(");
		}

		user.setAddress(savedAddress);
		existingUser = this.userService.addUser(user);

		if (existingUser == null) {
			throw new UserSaveFailedException("Registration Failed because of Technical issue:(");
		}

		response.setResponseMessage("User registered Successfully");
		response.setSuccess(true);

		return new ResponseEntity<CommonApiResponse>(response, HttpStatus.OK);
	}

	public ResponseEntity<UserLoginResponse> login(UserLoginRequest loginRequest) {

		LOG.info("Received request for User Login");

		UserLoginResponse response = new UserLoginResponse();

		if (loginRequest == null) {
			response.setResponseMessage("Missing Input");
			response.setSuccess(false);

			return new ResponseEntity<UserLoginResponse>(response, HttpStatus.BAD_REQUEST);
		}

		String jwtToken = null;
		User user = null;

		List<GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority(loginRequest.getRole()));

		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getEmailId(),
					loginRequest.getPassword(), authorities));
		} catch (Exception ex) {
			response.setResponseMessage("Invalid email or password.");
			response.setSuccess(false);
			return new ResponseEntity<UserLoginResponse>(response, HttpStatus.BAD_REQUEST);
		}

		jwtToken = jwtUtils.generateToken(loginRequest.getEmailId());

		user = this.userService.getUserByEmailIdAndRoleAndStatus(loginRequest.getEmailId(), loginRequest.getRole(),
				UserStatus.ACTIVE.value());

		UserDto userDto = UserDto.toUserDtoEntity(user);

		// user is authenticated
		if (jwtToken != null) {
			response.setUser(userDto);
			response.setResponseMessage("Logged in sucessful");
			response.setSuccess(true);
			response.setJwtToken(jwtToken);
			return new ResponseEntity<UserLoginResponse>(response, HttpStatus.OK);
		}

		else {
			response.setResponseMessage("Failed to login");
			response.setSuccess(false);
			return new ResponseEntity<UserLoginResponse>(response, HttpStatus.BAD_REQUEST);
		}

	}

	public ResponseEntity<UserResponseDto> getUsersByRole(String role) {

		UserResponseDto response = new UserResponseDto();

		if (role == null) {
			response.setResponseMessage("missing role");
			response.setSuccess(false);
			return new ResponseEntity<UserResponseDto>(response, HttpStatus.BAD_REQUEST);
		}

		List<User> users = new ArrayList<>();

		users = this.userService.getUserByRoleAndStatus(role, UserStatus.ACTIVE.value());

		if (users.isEmpty()) {
			response.setResponseMessage("No Users Found");
			response.setSuccess(false);
		}

		List<UserDto> userDtos = new ArrayList<>();

		for (User user : users) {

			UserDto dto = UserDto.toUserDtoEntity(user);

			if (role.equals(UserRole.ROLE_DELIVERY.value())) {

				UserDto sellerDto = UserDto.toUserDtoEntity(user.getSeller());
				dto.setSeller(sellerDto);

			}

			userDtos.add(dto);

		}

		response.setUsers(userDtos);
		response.setResponseMessage("User Fetched Successfully");
		response.setSuccess(true);

		return new ResponseEntity<UserResponseDto>(response, HttpStatus.OK);
	}

	public ResponseEntity<CommonApiResponse> updateUserStatus(UserStatusUpdateRequestDto request) {

		LOG.info("Received request for updating the user status");

		CommonApiResponse response = new CommonApiResponse();

		if (request == null) {
			response.setResponseMessage("bad request, missing data");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		if (request.getUserId() == 0) {
			response.setResponseMessage("bad request, user id is missing");
			response.setSuccess(false);

			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User user = null;
		user = this.userService.getUserById(request.getUserId());

		user.setStatus(request.getStatus());

		User updatedUser = this.userService.updateUser(user);

		if (updatedUser == null) {
			throw new UserSaveFailedException("Failed to update the User status");
		}

		response.setResponseMessage("User " + request.getStatus() + " Successfully!!!");
		response.setSuccess(true);
		return new ResponseEntity<CommonApiResponse>(response, HttpStatus.OK);

	}

	public ResponseEntity<UserResponseDto> getDeliveryPersonsBySeller(int sellerId) {

		UserResponseDto response = new UserResponseDto();

		if (sellerId == 0) {
			response.setResponseMessage("missing seller id");
			response.setSuccess(false);
			return new ResponseEntity<UserResponseDto>(response, HttpStatus.BAD_REQUEST);
		}

		User seller = this.userService.getUserById(sellerId);

		if (seller == null) {
			response.setResponseMessage("Seller not found");
			response.setSuccess(false);
			return new ResponseEntity<UserResponseDto>(response, HttpStatus.BAD_REQUEST);
		}

		List<User> users = new ArrayList<>();

		users = this.userService.getUserBySellerAndRoleAndStatusIn(seller, UserRole.ROLE_DELIVERY.value(),
				Arrays.asList(UserStatus.ACTIVE.value()));

		if (users.isEmpty()) {
			response.setResponseMessage("No Delivery Guys Found");
			response.setSuccess(false);
		}

		List<UserDto> userDtos = new ArrayList<>();

		for (User user : users) {

			UserDto dto = UserDto.toUserDtoEntity(user);
			userDtos.add(dto);

		}

		response.setUsers(userDtos);
		response.setResponseMessage("User Fetched Successfully");
		response.setSuccess(true);

		return new ResponseEntity<UserResponseDto>(response, HttpStatus.OK);
	}

	public ResponseEntity<CommonApiResponse> deleteSeller(int sellerId) {

		UserResponseDto response = new UserResponseDto();

		if (sellerId == 0) {
			response.setResponseMessage("missing seller id");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User seller = this.userService.getUserById(sellerId);

		if (seller == null) {
			response.setResponseMessage("Seller not found");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		List<User> deliveryPersons = new ArrayList<>();

		List<Product> products = new ArrayList<>();

		deliveryPersons = this.userService.getUserBySellerAndRoleAndStatusIn(seller, UserRole.ROLE_DELIVERY.value(),
				Arrays.asList(UserStatus.ACTIVE.value()));

		products = this.productService.getAllProductBySellerAndStatusIn(seller,
				Arrays.asList(ProductStatus.AVAILABLE.value()));

		seller.setStatus(UserStatus.DEACTIVATED.value());
		User deletedSeller = this.userService.updateUser(seller);

		// deactivating the seller
		if (deletedSeller == null) {
			throw new UserSaveFailedException("Failed to deactivate the seller!!!");
		}

		// deactivating the all seller, delivery persons
		if (!deliveryPersons.isEmpty()) {

			for (User deliveryPerson : deliveryPersons) {
				deliveryPerson.setStatus(UserStatus.DEACTIVATED.value());
			}

			List<User> deletedDeliveryPerons = this.userService.updateAllUser(deliveryPersons);

			if (CollectionUtils.isEmpty(deletedDeliveryPerons)) {
				throw new UserSaveFailedException("Failed to deactivate the seller!!!");
			}

		}

		// deactivating all the listed products by seller
		if (!products.isEmpty()) {

			for (Product product : products) {
				product.setStatus(ProductStatus.DEACTIVATED.value());
			}

			List<Product> deletedProducts = this.productService.updateAllProduct(products);

			if (CollectionUtils.isEmpty(deletedProducts)) {
				throw new UserSaveFailedException("Failed to deactivate the seller!!!");
			}

		}

		response.setResponseMessage("Seller Deactivated Successful");
		response.setSuccess(true);

		return new ResponseEntity<CommonApiResponse>(response, HttpStatus.OK);

	}

	public ResponseEntity<CommonApiResponse> deleteDeliveryPerson(int deliveryId) {

		UserResponseDto response = new UserResponseDto();

		if (deliveryId == 0) {
			response.setResponseMessage("missing delivery person id");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User delivery = this.userService.getUserById(deliveryId);

		if (delivery == null) {
			response.setResponseMessage("Delivery Person not found");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		delivery.setStatus(UserStatus.DEACTIVATED.value());

		User deletedDelivery = this.userService.updateUser(delivery);

		// deactivating the seller
		if (deletedDelivery == null) {
			throw new UserSaveFailedException("Failed to deactivate the delivery person!!!");
		}

		response.setResponseMessage("Delivery Person Deactivated Successful");
		response.setSuccess(true);

		return new ResponseEntity<CommonApiResponse>(response, HttpStatus.OK);

	}

	public ResponseEntity<UserResponseDto> getUserById(int userId) {

		UserResponseDto response = new UserResponseDto();

		if (userId == 0) {
			response.setResponseMessage("Invalid Input");
			response.setSuccess(false);
			return new ResponseEntity<UserResponseDto>(response, HttpStatus.BAD_REQUEST);
		}

		List<User> users = new ArrayList<>();

		User user = this.userService.getUserById(userId);
		users.add(user);

		if (users.isEmpty()) {
			response.setResponseMessage("No Users Found");
			response.setSuccess(false);
			return new ResponseEntity<UserResponseDto>(response, HttpStatus.OK);
		}

		List<UserDto> userDtos = new ArrayList<>();

		for (User u : users) {

			UserDto dto = UserDto.toUserDtoEntity(u);

			if (u.getRole().equals(UserRole.ROLE_DELIVERY.value())) {

				UserDto sellerDto = UserDto.toUserDtoEntity(u.getSeller());
				dto.setSeller(sellerDto);

			}

			userDtos.add(dto);

		}

		response.setUsers(userDtos);
		response.setResponseMessage("User Fetched Successfully");
		response.setSuccess(true);

		return new ResponseEntity<UserResponseDto>(response, HttpStatus.OK);
	}

	public ResponseEntity<CommonApiResponse> updateUserWallet(User request) {

		CommonApiResponse response = new CommonApiResponse();

		if (request == null) {
			response.setResponseMessage("Invalid Input");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		if (request.getId() == 0 || request.getWalletAmount() == null
				|| request.getWalletAmount().compareTo(BigDecimal.ZERO) <= 0) {
			response.setResponseMessage("No Users Found");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		User customer = this.userService.getUserById(request.getId());

		if (customer == null || !customer.getRole().equals(UserRole.ROLE_CUSTOMER.value())) {
			response.setResponseMessage("Customer Not Found");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		customer.setWalletAmount(customer.getWalletAmount().add(request.getWalletAmount()));

		User updatedCustomer = this.userService.updateUser(customer);

		if (updatedCustomer == null) {
			response.setResponseMessage("Failed to update the Wallet");
			response.setSuccess(false);
			return new ResponseEntity<CommonApiResponse>(response, HttpStatus.BAD_REQUEST);
		}

		response.setResponseMessage("Customer Wallet Updated Successful!!!");
		response.setSuccess(true);

		return new ResponseEntity<CommonApiResponse>(response, HttpStatus.OK);
	}

}
