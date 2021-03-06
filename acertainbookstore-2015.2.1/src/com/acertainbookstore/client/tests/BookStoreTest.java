package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.ConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * Test class to test the BookStore interface
 * 
 */
public class BookStoreTest {

	private static final int TEST_ISBN = 3044560;
	private static final int NUM_COPIES = 5;
	private static final int newNumBooksA = 10;
	private static final int newNumBooksB = 13;
	private static boolean localTest = true;
	private static StockManager storeManager;
	private static BookStore client;
	private static boolean hasFailed = false; // used for test 2

	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System
					.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean
					.parseBoolean(localTestProperty) : localTest;
			if (localTest) {
                ConcurrentCertainBookStore store = new ConcurrentCertainBookStore();
				storeManager = store;
				client = store;
			} else {
				storeManager = new StockManagerHTTPProxy(
						"http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}
			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones",
				"George RR Testin'", (float) 10, copies, 0, 0, 0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit",
				"JK Unit", (float) 10, NUM_COPIES, 0, 0, 0, false);
	}

	public StockBook getDefaultBook2(int i) {
		return new ImmutableStockBook(TEST_ISBN + 1, "Dansens estitik og historie",
				"Unknown", (float) 10, i, 0, 0, 0, false);
	}
	
	public StockBook getDefaultBook3(int i) {
		return new ImmutableStockBook(TEST_ISBN + 2, "Introduction to Eduroam, vol 0",
				"Hilterik Smørhår", (float) 10, i, 0, 0, 0, false);
	}
	
	/**
	 * Method to add a book, executed before every test case is run
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN()
				&& bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor())
				&& bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getSaleMisses() == addedBook.getSaleMisses()
				&& bookInList.getAverageRating() == addedBook
						.getAverageRating()
				&& bookInList.getTimesRated() == addedBook.getTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());

	}
	
	private class ClientRunnable implements Runnable {
		private boolean job;
		
		ClientRunnable(boolean i) {
			this.job = i;
		}
		
		public void run() {
			
			HashSet<BookCopy> bookToBuy = new HashSet<BookCopy>();
			bookToBuy.add(new BookCopy(TEST_ISBN, 2));
			bookToBuy.add(new BookCopy(TEST_ISBN + 1, 1));
			bookToBuy.add(new BookCopy(TEST_ISBN + 2, 3));

			for(int i = 0; i < 100000; i++) {
				if(job) {
					try {
						client.buyBooks(bookToBuy);
					} catch (BookStoreException e) {
						try {
							Thread.sleep(2);
						} catch (InterruptedException e1) {
							;
						}
						i--;
					}
				} else {
					try {
						storeManager.addCopies(bookToBuy);
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	@Test
	public void testConcurrentBuyStock() throws BookStoreException {

		
		HashSet<StockBook> bookToAdd = new HashSet<StockBook>();
		bookToAdd.add(getDefaultBook2(newNumBooksA));
		bookToAdd.add(getDefaultBook3(newNumBooksB));
		try {
			storeManager.addBooks(bookToAdd);
		} catch (BookStoreException e) {
			fail();
		}

		Thread preben = new Thread(new ClientRunnable(true));
		Thread sigurd = new Thread(new ClientRunnable(false));

		sigurd.start();
		preben.start();

		try{
			sigurd.join();
			preben.join();
		}catch (Exception e){
			fail();
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		// Check pre and post state are same
		bookToAdd.add(getDefaultBook());
		assertTrue(bookToAdd.containsAll(booksInStorePostTest));
		assertTrue(bookToAdd.size() == booksInStorePostTest.size());
		for(StockBook sb : storeManager.getBooks()) {
			assertTrue(sb.getNumCopies() == NUM_COPIES && sb.getISBN() == TEST_ISBN
					  || sb.getNumCopies() == newNumBooksA && sb.getISBN() == TEST_ISBN+1
					  || sb.getNumCopies() == newNumBooksB && sb.getISBN() == TEST_ISBN+2
					  );
		}
	}
	
	private class ClientRunnable2 implements Runnable {
		private boolean job;
		private int loopSize;
		
		ClientRunnable2(boolean i, int n) {
			this.job = i;
			loopSize = n;
		}
		
		public void run() {
			
			HashSet<BookCopy> bookToBuy = new HashSet<BookCopy>();
			bookToBuy.add(new BookCopy(TEST_ISBN, 2));
			bookToBuy.add(new BookCopy(TEST_ISBN + 1, 1));
			bookToBuy.add(new BookCopy(TEST_ISBN + 2, 3));

			for(int i = 0; i < loopSize; i++) {
				if(job) {
					try {
						client.buyBooks(bookToBuy);
						storeManager.addCopies(bookToBuy);
					} catch (BookStoreException e) {
						return;
					}
				} else {
					try {
						for(StockBook sb : storeManager.getBooks()) {
							if(sb.getNumCopies() == NUM_COPIES && sb.getISBN() == TEST_ISBN
									  || sb.getNumCopies() == NUM_COPIES - 2 && sb.getISBN() == TEST_ISBN
									  || sb.getNumCopies() == newNumBooksA && sb.getISBN() == TEST_ISBN+1
									  || sb.getNumCopies() == newNumBooksA - 1 && sb.getISBN() == TEST_ISBN+1
									  || sb.getNumCopies() == newNumBooksB && sb.getISBN() == TEST_ISBN+2
									  || sb.getNumCopies() == newNumBooksB  -3 && sb.getISBN() == TEST_ISBN+2
									  ){

							}else{
								hasFailed = true;
								return;
							}
						}
					} catch (BookStoreException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	@Test
	public void testConcurrentBuyStock2() throws BookStoreException {

		hasFailed = false;
		HashSet<StockBook> bookToAdd = new HashSet<StockBook>();
		int newNumBooksA = 10;
		int newNumBooksB = 13;
		bookToAdd.add(getDefaultBook2(newNumBooksA));
		bookToAdd.add(getDefaultBook3(newNumBooksB));
		try {
			storeManager.addBooks(bookToAdd);
		} catch (BookStoreException e) {
			fail();
		}
		
		Thread preben = new Thread(new ClientRunnable2(true, 500000));
		Thread sigurd = new Thread(new ClientRunnable2(false, 400000));

		sigurd.start();
		preben.start();
		
		try{
			sigurd.join();
			preben.join();
		}catch (Exception e){
			fail();
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		// Check pre and post state are same
		bookToAdd.add(getDefaultBook());
		assertTrue(bookToAdd.containsAll(booksInStorePostTest));
		assertTrue(bookToAdd.size() == booksInStorePostTest.size());
		assertTrue(!hasFailed);
	}
	@Test
	public void testConcurrentBuyStock3() throws BookStoreException {

		HashSet<StockBook> bookToAdd = new HashSet<StockBook>();
		int newNumBooksA = 10;
		int newNumBooksB = 13;
		bookToAdd.add(getDefaultBook2(newNumBooksA));
		bookToAdd.add(getDefaultBook3(newNumBooksB));
		try {
			storeManager.addBooks(bookToAdd);
		} catch (BookStoreException e) {
			fail();
		}
		List<Thread> threads = new ArrayList<Thread>();
		for(int i = 0; i<100; i++){
			threads.add(new Thread(new ClientRunnable(true)));	
			threads.add(new Thread(new ClientRunnable(false)));
		}

		for(Thread t : threads){
			t.start();
		}
		
		try{
			for(Thread t : threads)
				t.join();
		}catch (Exception e){
			fail();
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		// Check pre and post state are same
		bookToAdd.add(getDefaultBook());
		assertTrue(bookToAdd.containsAll(booksInStorePostTest));
		assertTrue(bookToAdd.size() == booksInStorePostTest.size());
		for(StockBook sb : storeManager.getBooks()) {
			assertTrue(sb.getNumCopies() == NUM_COPIES && sb.getISBN() == TEST_ISBN
					  || sb.getNumCopies() == newNumBooksA && sb.getISBN() == TEST_ISBN+1
					  || sb.getNumCopies() == newNumBooksB && sb.getISBN() == TEST_ISBN+2
					  );
		}
	}
	
	private class ClientRunnable4 implements Runnable {
		
		ClientRunnable4() {
		}
		
		public void run() {

			for(int i = 0; i < 100000; i++) {
				try {
					storeManager.getBooks();
					client.getEditorPicks(1);
				} catch (BookStoreException e) {
					hasFailed = true;
				}
			}
		}
	}
	@Test
	public void testConcurrentBuyStock4() throws BookStoreException {

		hasFailed = false;
		HashSet<StockBook> bookToAdd = new HashSet<StockBook>();
		int newNumBooksA = 10;
		int newNumBooksB = 13;
		bookToAdd.add(getDefaultBook2(newNumBooksA));
		bookToAdd.add(getDefaultBook3(newNumBooksB));
		try {
			storeManager.addBooks(bookToAdd);
		} catch (BookStoreException e) {
			fail();
		}
		List<Thread> threads = new ArrayList<Thread>();
		for(int i = 0; i<100; i++){
			threads.add(new Thread(new ClientRunnable4()));
		}

		for(Thread t : threads){
			t.start();
		}
		
		try{
			for(Thread t : threads)
				t.join();
		}catch (Exception e){
			fail();
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		// Check pre and post state are same
		bookToAdd.add(getDefaultBook());
		assertTrue(bookToAdd.containsAll(booksInStorePostTest));
		assertTrue(bookToAdd.size() == booksInStorePostTest.size());
		for(StockBook sb : storeManager.getBooks()) {
			assertTrue(sb.getNumCopies() == NUM_COPIES && sb.getISBN() == TEST_ISBN
					  || sb.getNumCopies() == newNumBooksA && sb.getISBN() == TEST_ISBN+1
					  || sb.getNumCopies() == newNumBooksB && sb.getISBN() == TEST_ISBN+2
					  );
		}
		assertTrue(!hasFailed);
	}
	/**
	 * Tests that books with invalid ISBNs cannot be bought
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid isbn
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		// Check pre and post state are same
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());

	}

	/**
	 * Tests that books can only be bought if they are in the book store
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with isbn which does not exist
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		// Check pre and post state are same
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());

	}

	/**
	 * Tests that you can't buy more books than there are copies
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());

	}

	/**
	 * Tests that you can't buy a negative number of books
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());

	}

    /**
	 * Tests that all books can be retrieved
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1,
				"The Art of Computer Programming", "Donald Knuth", (float) 300,
				NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2,
				"The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES,
				0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other
		assertTrue(listBooks.containsAll(booksAdded)
				&& listBooks.size() == booksAdded.size());
	}

    /**
	 * Tests that a list of books with a certain feature can be retrieved
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1,
				"The Art of Computer Programming", "Donald Knuth", (float) 300,
				NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2,
				"The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES,
				0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN

		List<Book> books = client.getBooks(isbnList);
		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd)
				&& books.size() == booksToAdd.size());

	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());

	}

	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();
		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}

}
