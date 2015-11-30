/**
 *
 */
package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/**
 * ConcurrentCertainBookStore implements the bookstore and its functionality which is
 * defined in the BookStore
 */
public class ConcurrentCertainBookStore implements BookStore, StockManager {
	private Map<Integer, BookStoreBook> bookMap;
	private Map<Integer, ReadWriteLock> lockMap;
	private ReadWriteLock masterLock;
	
	public ConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<Integer, BookStoreBook>();
		lockMap = new HashMap<Integer, ReadWriteLock>();
		masterLock = new ReentrantReadWriteLock();
	}

	public void addBooks(Set<StockBook> bookSet)
			throws BookStoreException {
		
		if (bookSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		Lock myML = masterLock.writeLock();
		List<Lock> locks = new ArrayList<Lock>();
		
		// Check if all are there
		for (StockBook book : bookSet) {
			int ISBN = book.getISBN();
			String bookTitle = book.getTitle();
			String bookAuthor = book.getAuthor();
			int noCopies = book.getNumCopies();
			float bookPrice = book.getPrice();
			
			
			if (BookStoreUtility.isInvalidISBN(ISBN)
					|| BookStoreUtility.isEmpty(bookTitle)
					|| BookStoreUtility.isEmpty(bookAuthor)
					|| BookStoreUtility.isInvalidNoCopies(noCopies)
					|| bookPrice < 0.0) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.BOOK
						+ book.toString() + BookStoreConstants.INVALID);
			} else if (bookMap.containsKey(ISBN)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.DUPLICATED);
			}
		}


		
		for (StockBook book : bookSet) {
			int ISBN = book.getISBN();
			lockMap.put(ISBN, new ReentrantReadWriteLock());
			ReadWriteLock newLock = lockMap.get(ISBN);
			locks.add(newLock.writeLock());
			
			bookMap.put(ISBN, new BookStoreBook(book));
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
		
		return;
	}

	public void addCopies(Set<BookCopy> bookCopiesSet)
			throws BookStoreException {
		Lock myML = masterLock.readLock();
		int ISBN, numCopies;
		List<Lock> locks = new ArrayList<Lock>();

		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		for (BookCopy bookCopy : bookCopiesSet) {
			ISBN = bookCopy.getISBN();
			
			numCopies = bookCopy.getNumCopies();
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);}
			if (!bookMap.containsKey(ISBN)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);}
			
			if (BookStoreUtility.isInvalidNoCopies(numCopies)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.NUM_COPIES
						+ numCopies + BookStoreConstants.INVALID);}

		}

		BookStoreBook book;
		// Update the number of copies
		for (BookCopy bookCopy : bookCopiesSet) {
			ISBN = bookCopy.getISBN();
			ReadWriteLock newLock = lockMap.get(ISBN);
			locks.add(newLock.writeLock());
			numCopies = bookCopy.getNumCopies();
			book = bookMap.get(ISBN);
			book.addCopies(numCopies);
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
	}

	public List<StockBook> getBooks() {
		Lock myML = masterLock.readLock();
		Collection<ReadWriteLock> myLocks = lockMap.values();
		List<Lock> locks = new ArrayList<Lock>();
		for(ReadWriteLock lock : myLocks) {
			locks.add(lock.readLock());
		}
		
		List<StockBook> listBooks = new ArrayList<StockBook>();
		Collection<BookStoreBook> bookMapValues = bookMap.values();
		for (BookStoreBook book : bookMapValues) {
			listBooks.add(book.immutableStockBook());
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
		
		return listBooks;
	}

	public void updateEditorPicks(Set<BookEditorPick> editorPicks)
			throws BookStoreException {

		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		Lock myML = masterLock.readLock();
		int ISBNVal;
		List<Lock> locks = new ArrayList<Lock>();


		for (BookEditorPick editorPickArg : editorPicks) {
			ISBNVal = editorPickArg.getISBN();
			
			
			if (BookStoreUtility.isInvalidISBN(ISBNVal)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBNVal
						+ BookStoreConstants.INVALID);}
			if (!bookMap.containsKey(ISBNVal)){
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBNVal
						+ BookStoreConstants.NOT_AVAILABLE);}
			

		}

		for (BookEditorPick editorPickArg : editorPicks) {
			ReadWriteLock newLock = lockMap.get(editorPickArg.getISBN());
			locks.add(newLock.writeLock());
			
			bookMap.get(editorPickArg.getISBN()).setEditorPick(
					editorPickArg.isEditorPick());
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
		
		return;
	}

	public void buyBooks(Set<BookCopy> bookCopiesToBuy)
			throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		Lock myML = masterLock.readLock();
		// Check that all ISBNs that we buy are there first.
		int ISBN;
		List<Lock> locks = new ArrayList<Lock>();

		
		BookStoreBook book;
		Boolean saleMiss = false;
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			ISBN = bookCopyToBuy.getISBN();
			if (bookCopyToBuy.getNumCopies() < 0) {
				for(Lock lock : locks) {
					lock.unlock();
				}
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.NUM_COPIES
						+ bookCopyToBuy.getNumCopies()
						+ BookStoreConstants.INVALID);
			}
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				for(Lock lock : locks) {
					lock.unlock();
				}
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);}
			if (!bookMap.containsKey(ISBN)) {
				for(Lock lock : locks) {
					lock.unlock();
				}
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);}
			
			locks.add(lockMap.get(ISBN).writeLock());
			
			book = bookMap.get(ISBN);
			if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
				book.addSaleMiss(); // If we cannot sell the copies of the book
									// its a miss
				saleMiss = true;
			}
		}

		// We throw exception now since we want to see how many books in the
		// order incurred misses which is used by books in demand
		if (saleMiss) {
			for(Lock lock : locks) {
				lock.unlock();
			}
			myML.unlock();
			throw new BookStoreException(BookStoreConstants.BOOK
					+ BookStoreConstants.NOT_AVAILABLE);}

		// Then make purchase
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			book = bookMap.get(bookCopyToBuy.getISBN());
			book.buyCopies(bookCopyToBuy.getNumCopies());
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
		return;
	}


	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet)
			throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		Lock myML = masterLock.readLock();

		List<Lock> locks = new ArrayList<Lock>();

		
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)){
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);}
			if (!bookMap.containsKey(ISBN)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);}

		}

		List<StockBook> listBooks = new ArrayList<StockBook>();

		for (Integer ISBN : isbnSet) {
			locks.add(lockMap.get(ISBN).readLock());
			listBooks.add(bookMap.get(ISBN).immutableStockBook());
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();

		return listBooks;
	}

	public List<Book> getBooks(Set<Integer> isbnSet)
			throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		Lock myML = masterLock.readLock();

		List<Lock> locks = new ArrayList<Lock>();

		
		// Check that all ISBNs that we rate are there first.
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);}
			if (!bookMap.containsKey(ISBN)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);}
			

		}

		List<Book> listBooks = new ArrayList<Book>();

		// Get the books
		for (Integer ISBN : isbnSet) {
			locks.add(lockMap.get(ISBN).readLock());
			listBooks.add(bookMap.get(ISBN).immutableBook());
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
		return listBooks;
	}

	public List<Book> getEditorPicks(int numBooks)
			throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks
					+ ", but it must be positive");
		}
		Lock myML = masterLock.readLock();

		List<Lock> locks = new ArrayList<Lock>();
		Collection<ReadWriteLock> allLocks = lockMap.values();
		
		for(ReadWriteLock lock : allLocks) {
			locks.add(lock.readLock());
		}


		List<BookStoreBook> listAllEditorPicks = new ArrayList<BookStoreBook>();
		List<Book> listEditorPicks = new ArrayList<Book>();
		Iterator<Entry<Integer, BookStoreBook>> it = bookMap.entrySet()
				.iterator();
		BookStoreBook book;

		// Get all books that are editor picks
		while (it.hasNext()) {
			Entry<Integer, BookStoreBook> pair = (Entry<Integer, BookStoreBook>) it
					.next();
			book = (BookStoreBook) pair.getValue();
			if (book.isEditorPick()) {
				listAllEditorPicks.add(book);
			}
		}

		// Find numBooks random indices of books that will be picked
		Random rand = new Random();
		Set<Integer> tobePicked = new HashSet<Integer>();
		int rangePicks = listAllEditorPicks.size();
		if (rangePicks <= numBooks) {
			// We need to add all the books
			for (int i = 0; i < listAllEditorPicks.size(); i++) {
				tobePicked.add(i);
			}
		} else {
			// We need to pick randomly the books that need to be returned
			int randNum;
			while (tobePicked.size() < numBooks) {
				randNum = rand.nextInt(rangePicks);
				tobePicked.add(randNum);
			}
		}

		// Get the numBooks random books
		for (Integer index : tobePicked) {
			book = listAllEditorPicks.get(index);
			listEditorPicks.add(book.immutableBook());
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
		return listEditorPicks;

	}

	@Override
	public List<Book> getTopRatedBooks(int numBooks)
			throws BookStoreException {
		throw new BookStoreException("Not implemented");
	}

	@Override
	public List<StockBook> getBooksInDemand()
			throws BookStoreException {
		throw new BookStoreException("Not implemented");
	}

	@Override
	public void rateBooks(Set<BookRating> bookRating)
			throws BookStoreException {
		throw new BookStoreException("Not implemented");
	}

	public void removeAllBooks() throws BookStoreException {
		Lock myML = masterLock.writeLock();

		List<Lock> locks = new ArrayList<Lock>();
		Collection<ReadWriteLock> allLocks = lockMap.values();
		for(ReadWriteLock lock : allLocks) {
			locks.add(lock.writeLock());
		}
		
		bookMap.clear();
		lockMap.clear();
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
		
	}

	public void removeBooks(Set<Integer> isbnSet)
			throws BookStoreException {


		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		Lock myML = masterLock.writeLock();
		List<Lock> locks = new ArrayList<Lock>();

		
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.INVALID);}
			if (!bookMap.containsKey(ISBN)){
				myML.unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN
						+ BookStoreConstants.NOT_AVAILABLE);}
		}

		for (int isbn : isbnSet) {
			locks.add(lockMap.get(isbn).writeLock());
			bookMap.remove(isbn);
			lockMap.remove(isbn);
		}
		
		for(Lock lock : locks) {
			lock.unlock();
		}
		myML.unlock();
	}
}
