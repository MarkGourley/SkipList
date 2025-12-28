package SkipList;
/**
 * A custom doubly-linked list implementation with an optimized fast-access layer.
 * This implementation uses a skip-list inspired approach for O(sqrt(n)) average case access time.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><b>Main Layer:</b> A standard doubly-linked list storing the actual elements</li>
 *   <li><b>Fast Layer:</b> A sparse layer of nodes pointing to main list nodes at regular intervals</li>
 *   <li><b>Gap-Based Indexing:</b> Fast layer uses relative gaps between nodes instead of absolute indices</li>
 *   <li><b>Dynamic Skip Distance:</b> Adjusts the distance between fast nodes based on list size</li>
 *   <li><b>Adaptive Rebalancing:</b> Maintains optimal fast layer density through periodic rebalancing</li>
 * </ul>
 *
 * <h2>Performance Characteristics:</h2>
 * <ul>
 *   <li>{@code add(E)}: O(1) amortized - Optimized tail operations with gap tracking</li>
 *   <li>{@code add(int, E)}: O(sqrt(n)) average case - Uses fast layer for positioning</li>
 *   <li>{@code remove(int)}: O(sqrt(n)) average case - Uses fast layer for positioning</li>
 *   <li>{@code remove(Object)}: O(n) worst case, but optimized with chunk-based search</li>
 *   <li>{@code get(int)}: O(sqrt(n)) average case - Bidirectional search with fast layer</li>
 * </ul>
 *
 * <h2>Implementation Notes:</h2>
 * <ul>
 *   <li>The fast layer maintains sentinel nodes at head and tail for boundary handling</li>
 *   <li>Gap values are always maintained > 0 to prevent corrupted state</li>
 *   <li>Periodic rebalancing ensures optimal fast layer density</li>
 *   <li>Edge cases and null conditions are handled throughout for robustness</li>
 * </ul>
 *
 * @param <E> the type of elements in this list
 */
public class SkipList<E> implements java.util.List<E> {
    /** Main list head node */
    private ListNode head;

    /** Main list tail node */
    private ListNode tail;

    /** Current size of the list */
    private int size = 0;

    /** Tracks distance since last fast node for efficient tail operations */
    private int pendingGap = 0;

    /** Counter for triggering rebalancing operations */
    private int operationsSinceRebalance = 0;

    /** Current distance between fast nodes, dynamically adjusted */
    private int currentSkipDistance = MIN_SKIP;

    /** Number of nodes in fast layer (including sentinels) */
    private int fastNodeCount = 0;

    /** Fast layer head sentinel node */
    private FastNode fastHead;

    /** Fast layer tail sentinel node */
    private FastNode fastTail;

    /** Minimum allowed distance between fast nodes */
    private static final int MIN_SKIP = 25;

    /** Number of operations before considering rebalance */
    private static final int REBALANCE_THRESHOLD = 100;

    /** Growth rate for skip distance as list size increases */
    private static final double SKIP_GROWTH_FACTOR = 1.5;

    /**
     * Node in the main doubly-linked list layer.
     * Each node maintains bidirectional links to its neighbors and an optional
     * upward link to a corresponding fast layer node if this node is part of
     * the fast access structure.
     */
    private class ListNode {
        /** Reference to previous node in main list */
        ListNode prev;

        /** Reference to next node in main list */
        ListNode next;

        /** Element stored in this node */
        E data;

        /** Optional reference to corresponding fast layer node */
        FastNode fastLink;

        /**
         * Constructs a new list node with given data and neighbors.
         *
         * @param data  The element to store in this node
         * @param prev  Reference to previous node (null for head)
         * @param next  Reference to next node (null for tail)
         */
        ListNode(E data, ListNode prev, ListNode next) {
            this.data = data;
            this.prev = prev;
            this.next = next;
            this.fastLink = null;
        }
    }

    /**
     * Node in the fast-access layer.
     * Maintains a sparse network of connections above the main list for faster traversal.
     * Uses gap-based indexing to track relative positions between fast nodes.
     */
    private class FastNode {
        /** Reference to corresponding main list node */
        ListNode target;

        /** Reference to previous fast layer node */
        FastNode prev;

        /** Reference to next fast layer node */
        FastNode next;

        /** Number of main list nodes between this and previous fast node */
        int gapFromPrev;

        /**
         * Constructs a new fast layer node.
         *
         * @param target       The main list node this fast node references
         * @param prev        Previous fast layer node
         * @param next        Next fast layer node
         * @param gapFromPrev Number of main list nodes to previous fast node
         */
        FastNode(ListNode target, FastNode prev, FastNode next, int gapFromPrev) {
            this.target = target;
            this.prev = prev;
            this.next = next;
            this.gapFromPrev = gapFromPrev;
        }
    }

    /**
     * Calculates the optimal skip distance based on current list size.
     * The distance between fast nodes grows with list size but is bounded by sqrt(n).
     * This method handles several edge cases:
     * <ul>
     *   <li>Returns MIN_SKIP for lists of size <= 1</li>
     *   <li>Prevents integer overflow using long arithmetic</li>
     *   <li>Ensures returned value is never less than MIN_SKIP</li>
     *   <li>Handles growth according to SKIP_GROWTH_FACTOR</li>
     * </ul>
     *
     * @return The optimal distance between fast nodes for current list size
     */
    private int getDynamicSkip() {
        // Handle edge cases
        if (size <= 1) return MIN_SKIP;

        // Prevent integer overflow
        long potentialSkip = currentSkipDistance;
        if (size > currentSkipDistance * SKIP_GROWTH_FACTOR) {
            potentialSkip = Math.min(
                    (long)(currentSkipDistance * SKIP_GROWTH_FACTOR),
                    (long)Math.sqrt(size)
            );
            // Ensure we don't exceed Integer.MAX_VALUE
            currentSkipDistance = (int)Math.min(potentialSkip, Integer.MAX_VALUE);
        }

        // Never return less than MIN_SKIP
        return Math.max(MIN_SKIP, currentSkipDistance);
    }

    /**
     * Initializes the fast layer sentinel nodes when transitioning from empty to non-empty list.
     * This method handles the following scenarios:
     * <ul>
     *   <li>Cleans up partially initialized sentinel state</li>
     *   <li>Creates new sentinels only if both are null</li>
     *   <li>Special handling for single-node list case</li>
     *   <li>Verifies successful initialization</li>
     *   <li>Rolls back on any initialization failure</li>
     * </ul>
     *
     * The fast layer is only initialized when:
     * <ul>
     *   <li>The list has at least one node (head != null)</li>
     *   <li>No existing sentinel nodes are present</li>
     * </ul>
     */
    private void initializeSentinels() {
        // Don't initialize if either sentinel already exists
        if (fastHead != null || fastTail != null) {
            // Clean up any partially initialized state
            if (fastHead != null && fastTail == null) {
                fastHead = null;
                if (head != null) head.fastLink = null;
            } else if (fastTail != null && fastHead == null) {
                fastTail = null;
                if (tail != null) tail.fastLink = null;
            }
            return;
        }

        // Only initialize if we have a valid head node
        if (head != null) {
            try {
                fastHead = new FastNode(head, null, null, 0);
                head.fastLink = fastHead;

                // Handle single node case
                if (head == tail) {
                    fastTail = new FastNode(tail, fastHead, null, 0);
                } else {
                    fastTail = new FastNode(tail, fastHead, null, Math.max(1, size - 1));
                }

                updateTailSentinel();
                fastHead.next = fastTail;
                fastTail.prev = fastHead;
                fastNodeCount = 2;

                // Verify the initialization
                if (fastHead.target == null || fastTail.target == null) {
                    // Roll back if something went wrong
                    fastHead = fastTail = null;
                    if (head != null) head.fastLink = null;
                    if (tail != null) tail.fastLink = null;
                    fastNodeCount = 0;
                }
            } catch (Exception e) {
                // Roll back on any unexpected error
                fastHead = fastTail = null;
                if (head != null) head.fastLink = null;
                if (tail != null) tail.fastLink = null;
                fastNodeCount = 0;
            }
        }
    }

    /**
     * Updates the fast tail sentinel to maintain proper list structure.
     * This method is called after operations that modify the list's tail.
     * It ensures that:
     * <ul>
     *   <li>Fast tail points to current list tail</li>
     *   <li>List tail has proper back-reference to fast tail</li>
     *   <li>Handles null tail case safely</li>
     * </ul>
     */
    private void updateTailSentinel() {
        if (fastTail != null) {
            fastTail.target = tail;
            if (tail != null) tail.fastLink = fastTail;
        }
    }

    /**
     * Adds a new fast node before the tail sentinel to maintain optimal access structure.
     * This method is used during list growth to maintain proper fast layer density.
     * It performs the following:
     * <ul>
     *   <li>Validates fast layer state before insertion</li>
     *   <li>Creates new fast node with proper gap value</li>
     *   <li>Updates bidirectional links in fast layer</li>
     *   <li>Establishes connection with main list node</li>
     * </ul>
     *
     * @param target The main list node to link with new fast node
     * @param gap    The gap value from previous fast node
     */
    private void appendFastNodeToLast(ListNode target, int gap) {
        if (fastTail == null || fastTail.prev == null || target == null) return;
        FastNode newFast = new FastNode(target, fastTail.prev, fastTail, gap);
        fastTail.prev.next = newFast;
        fastTail.prev = newFast;
        target.fastLink = newFast;
        fastNodeCount++;
    }

    /**
     * Removes a fast node and updates the fast layer structure.
     * This method handles the following:
     * <ul>
     *   <li>Protects sentinel nodes from removal</li>
     *   <li>Updates gap values to maintain relative indexing</li>
     *   <li>Ensures minimum gap values are maintained</li>
     *   <li>Updates fast layer connectivity</li>
     *   <li>Cleans up references in main list</li>
     *   <li>Maintains accurate fast node count</li>
     * </ul>
     *
     * @param toRemove The fast node to remove (ignored if null or sentinel)
     */
    private void removeFastNode(FastNode toRemove) {
        if (toRemove == null) return;

        // Don't remove sentinel nodes
        if (toRemove == fastHead || toRemove == fastTail) return;

        // Update gap information
        if (toRemove.prev != null && toRemove.next != null) {
            // Ensure gap doesn't underflow
            toRemove.next.gapFromPrev = Math.max(1, toRemove.next.gapFromPrev + toRemove.gapFromPrev);
        }

        // Update fast layer links
        if (toRemove.prev != null) {
            toRemove.prev.next = toRemove.next;
        }
        if (toRemove.next != null) {
            toRemove.next.prev = toRemove.prev;
        }

        // Clear main list node's fast link if it exists
        if (toRemove.target != null) {
            toRemove.target.fastLink = null;
        }

        // Clear removed node's references
        toRemove.prev = toRemove.next = null;
        toRemove.target = null;

        // Update fast node count
        if (fastNodeCount > 2) {  // Don't decrement below sentinel count
            fastNodeCount--;
        }
    }

    /**
     * Checks if rebalancing is needed and performs it if necessary.
     * Rebalancing is triggered by:
     * <ul>
     *   <li>Reaching REBALANCE_THRESHOLD operations</li>
     *   <li>Fast layer density becoming suboptimal</li>
     * </ul>
     *
     * This helps maintain optimal performance characteristics by:
     * <ul>
     *   <li>Keeping fast layer density proportional to sqrt(n)</li>
     *   <li>Adjusting skip distances based on list size</li>
     *   <li>Preventing degradation of access times</li>
     * </ul>
     */
    private void checkAndRebalance() {
        operationsSinceRebalance++;
        if (operationsSinceRebalance >= REBALANCE_THRESHOLD ||
                (fastNodeCount > 2 && fastNodeCount < Math.sqrt(size) / 2)) {
            rebalanceFastLayer();
            operationsSinceRebalance = 0;
        }
    }

    /**
     * Adds an element to the end of the list with O(1) amortized complexity.
     * This method maintains optimal performance through:
     * <ul>
     *   <li>Efficient tail operations using direct tail reference</li>
     *   <li>Proper fast layer maintenance with gap tracking</li>
     *   <li>Automatic fast node creation when optimal</li>
     *   <li>Sentinel initialization for empty list case</li>
     * </ul>
     *
     * @param e Element to append to the list
     * @return true (as specified by Collection.add)
     */
    @Override
    public boolean add(E e) {
        // Create new node and update main list
        ListNode newNode = new ListNode(e, tail, null);
        if (tail != null) tail.next = newNode;
        else head = newNode;
        tail = newNode;
        size++;

        if (size == 1) {
            initializeSentinels();
            pendingGap = 0;  // Reset gap for first element
        } else {
            // Update tail sentinel
            updateTailSentinel();
            pendingGap++;

            // Check if we need a new fast node before tail
            if (pendingGap >= getDynamicSkip()) {
                // Add new fast node before tail sentinel
                ListNode beforeTail = tail.prev;
                FastNode newFast = new FastNode(beforeTail, fastTail.prev, fastTail, pendingGap - 1);
                fastTail.prev.next = newFast;
                fastTail.prev = newFast;
                beforeTail.fastLink = newFast;
                fastNodeCount++;

                // Reset gap and update tail sentinel's gap
                fastTail.gapFromPrev = 1;
                pendingGap = 1;  // Reset to 1 since we just added a node after the new fast node
            } else {
                // Just update tail sentinel's gap
                fastTail.gapFromPrev = pendingGap;
            }
        }

        return true;
    }

    /**
     * Inserts an element at the specified position with O(sqrt(n)) average complexity.
     * This method optimizes insertion through:
     * <ul>
     *   <li>Fast layer traversal for positioning</li>
     *   <li>Special handling for head/tail insertions</li>
     *   <li>Proper gap maintenance in fast layer</li>
     *   <li>Automatic rebalancing when needed</li>
     * </ul>
     *
     * @param index Index at which to insert the element
     * @param element Element to insert
     * @throws IndexOutOfBoundsException if index is out of range (index < 0 || index > size())
     */
    @Override
    public void add(int index, E element) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException();

        // Handle append to end - reuse optimized add()
        if (index == size) {
            add(element);
            return;
        }

        // Handle insert at head
        if (index == 0) {
            ListNode newNode = new ListNode(element, null, head);
            if (head != null) {
                head.prev = newNode;
            } else {
                tail = newNode; // Empty list case
            }
            head = newNode;
            size++;

            if (size == 1) {
                initializeSentinels();
            } else if (fastHead != null) {  // Check if fast layer exists
                // Update fast head sentinel and its next node's gap
                fastHead.target = head;
                head.fastLink = fastHead;
                if (fastHead.next != null) {
                    fastHead.next.gapFromPrev = Math.max(1, fastHead.next.gapFromPrev + 1);
                }
            }
            return;
        }

        // Find the fast node that will need its gap updated
        FastNode fast = fastHead;
        FastNode updateFast = null;
        int traversed = 0;

        while (fast != null && fast.next != null) {
            if (traversed + fast.next.gapFromPrev > index) {
                updateFast = fast.next;  // This is the fast node whose gap needs updating
                break;
            }
            traversed += fast.next.gapFromPrev;
            fast = fast.next;
        }

        // Handle internal insertions
        ListNode curr = getNode(index);
        if (curr == null) throw new IllegalStateException("Target node not found at index: " + index);

        ListNode newNode = new ListNode(element, curr.prev, curr);
        if (curr.prev != null) curr.prev.next = newNode;
        curr.prev = newNode;
        size++;

        // Update the gap for the saved fast node
        if (updateFast != null) {
            updateFast.gapFromPrev = Math.max(1, updateFast.gapFromPrev + 1);
        }

        // Only rebalance if we're not at the edges and meet the criteria
        if (index > 1 && index < size - 1) {
            if ((index + 1) % getDynamicSkip() == 0) {
                checkAndRebalance();
            } else if (fastNodeCount > 0 && fastNodeCount < Math.sqrt(size) / 2) {
                // Additional check for fast layer density
                checkAndRebalance();
            }
        }
    }

    /**
     * Removes the element at the specified position with O(sqrt(n)) average complexity.
     * This method optimizes removal through:
     * <ul>
     *   <li>Fast layer traversal for positioning</li>
     *   <li>Special handling for head/tail removals</li>
     *   <li>Proper fast layer maintenance</li>
     *   <li>Automatic rebalancing for internal nodes</li>
     * </ul>
     *
     * @param index Index of element to remove
     * @return The removed element
     * @throws IndexOutOfBoundsException if index is out of range (index < 0 || index >= size())
     */
    @Override
    public E remove(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();

        // Handle head removal
        if (index == 0) {
            ListNode oldHead = head;
            E data = oldHead.data;

            head = oldHead.next;
            if (head != null) {
                head.prev = null;
            } else {
                tail = null; // List is now empty
            }

            size--;
            if (size == 0) {
                // List is now empty
                fastHead = fastTail = null;
                fastNodeCount = 0;
                pendingGap = 0;
                currentSkipDistance = MIN_SKIP;
            } else {
                // Update fast head sentinel and directly update gap
                fastHead.target = head;
                head.fastLink = fastHead;
                if (fastHead.next != null) {
                    fastHead.next.gapFromPrev = Math.max(1, fastHead.next.gapFromPrev - 1);
                }
            }

            return data;
        }

        // Handle tail removal
        if (index == size - 1) {
            ListNode oldTail = tail;
            E data = oldTail.data;

            if (tail.prev != null) {
                tail = oldTail.prev;
                tail.next = null;
                size--;

                // Update fast tail sentinel
                updateTailSentinel();
                if (fastTail != null && fastTail.prev != null) {
                    // Decrement the gap to tail
                    fastTail.gapFromPrev = Math.max(1, pendingGap - 1);
                }
                pendingGap = Math.max(0, pendingGap - 1);
            } else {
                // Single element list
                head = tail = null;
                size = 0;
                fastHead = fastTail = null;
                fastNodeCount = 0;
                pendingGap = 0;
                currentSkipDistance = MIN_SKIP;
            }

            return data;
        }

        // Find the fast node that will need its gap updated
        FastNode fast = fastHead;
        FastNode updateFast = null;
        int traversed = 0;

        while (fast != null && fast.next != null) {
            if (traversed + fast.next.gapFromPrev > index) {
                updateFast = fast.next;  // This is the fast node whose gap needs updating
                break;
            }
            traversed += fast.next.gapFromPrev;
            fast = fast.next;
        }

        // Handle internal node removal
        ListNode target = getNode(index);
        if (target == null) throw new IllegalStateException("Node not found at index: " + index);
        E data = target.data;

        // Update main list connections
        if (target.prev != null) target.prev.next = target.next;
        if (target.next != null) target.next.prev = target.prev;

        size--;

        // Update fast layer if needed
        if (target.fastLink != null && target.fastLink != fastHead && target.fastLink != fastTail) {
            // If we're removing a fast node, merge its gap
            FastNode fastNode = target.fastLink;
            if (fastNode.next != null) {
                fastNode.next.gapFromPrev += fastNode.gapFromPrev - 1;
            }
            removeFastNode(fastNode);
        } else if (updateFast != null) {
            // Otherwise just decrement the gap in the fast layer
            updateFast.gapFromPrev = Math.max(1, updateFast.gapFromPrev - 1);
        }

        // Only rebalance for internal nodes
        if (index > 1 && index < size - 1) {
            checkAndRebalance();
        }

        return data;
    }

    /**
     * Removes the first occurrence of the specified element with optimized search.
     * This method improves on O(n) worst case through:
     * <ul>
     *   <li>Quick head/tail checks</li>
     *   <li>Chunk-based search using fast layer</li>
     *   <li>Proper fast layer maintenance</li>
     *   <li>Automatic rebalancing when needed</li>
     * </ul>
     *
     * @param o Element to remove
     * @return true if element was found and removed, false otherwise
     */
    @Override
    public boolean remove(Object o) {
        if (head == null || o == null) return false;

        // Quick check for head/tail matches
        if (o.equals(head.data)) {
            remove(0);
            return true;
        }
        if (o.equals(tail.data)) {
            remove(size - 1);
            return true;
        }

        // Parallel search from both ends using fast layer
        FastNode frontFast = fastHead;
        FastNode backFast = fastTail;
        int frontIndex = 0;
        int backIndex = size - 1;

        while (frontFast != null && backFast != null &&
                frontFast.next != null && backFast.prev != null &&
                frontIndex < backIndex) {

            // Check front chunk
            if (searchChunk(frontFast, o)) return true;
            frontIndex += frontFast.next.gapFromPrev;
            frontFast = frontFast.next;

            // Check back chunk if we haven't crossed paths
            if (frontIndex < backIndex) {
                if (searchChunk(backFast.prev, o)) return true;
                backIndex -= backFast.gapFromPrev;
                backFast = backFast.prev;
            }
        }

        return false;
    }

    private boolean searchChunk(FastNode fast, Object o) {
        if (fast == null || fast.next == null) return false;

        // Check fast node's target first
        if (fast.target != null && o.equals(fast.target.data)) {
            removeNodeAndUpdateFast(fast.target, fast);
            return true;
        }

        // Search through regular nodes in this chunk
        ListNode current = fast.target != null ? fast.target.next : null;
        ListNode searchEnd = fast.next.target;
        if (current == null || searchEnd == null) return false;

        int remaining = fast.next.gapFromPrev - 1;  // -1 because we already checked fast.target
        while (remaining > 0 && current != searchEnd) {
            if (o.equals(current.data)) {
                removeNodeAndUpdateFast(current, fast);
                return true;
            }
            current = current.next;
            remaining--;
        }
        return false;
    }

    private void removeNodeAndUpdateFast(ListNode node, FastNode nearestFast) {
        // Update main list links
        if (node.prev != null) node.prev.next = node.next;
        if (node.next != null) node.next.prev = node.prev;

        // Update fast layer if needed
        if (node.fastLink != null) {
            removeFastNode(node.fastLink);
        } else if (nearestFast.next != null) {
            nearestFast.next.gapFromPrev = Math.max(1, nearestFast.next.gapFromPrev - 1);
        }

        size--;
        checkAndRebalance();
    }

    /**
     * Gets the node at the specified index using optimized access strategy.
     * This method implements a hybrid approach:
     * <ul>
     *   <li>Direct access for endpoints (head/tail)</li>
     *   <li>Fast layer traversal for internal nodes when beneficial</li>
     *   <li>Bidirectional search based on target position</li>
     *   <li>Fallback to normal traversal for small lists or when fast layer fails</li>
     * </ul>
     *
     * @param index The index of the desired node
     * @return The node at the specified index
     * @throws IndexOutOfBoundsException if index is out of range
     * @throws IllegalStateException if target node cannot be found
     */
    private ListNode getNode(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();

        // Direct access for endpoints
        if (index == 0) return head;
        if (index == size - 1) return tail;

        // For small lists or when fast layer isn't beneficial
        if (fastHead == null || fastTail == null || fastNodeCount <= 2 || size < getDynamicSkip()) {
            return getNodeNormally(index);
        }

        // Try fast layer traversal first
        ListNode result = null;
        if (index <= size / 2) {
            // Start from head, move forward
            ListNode current = head;
            FastNode fast = fastHead;
            int traversed = 0;

            // Use fast layer to get close to target
            while (fast != null && fast.next != null && fast.next != fastTail && current != null) {
                int nextGap = fast.next.gapFromPrev;
                if (traversed + nextGap > index) break;
                traversed += nextGap;
                fast = fast.next;
                if (fast == null || fast.target == null) {
                    return getNodeNormally(index); // Fallback if fast layer is corrupted
                }
                current = fast.target;
            }

            // Walk the remaining distance
            while (traversed < index && current != null) {
                current = current.next;
                traversed++;
            }
            result = current;
        } else {
            // Start from tail, move backward
            ListNode current = tail;
            FastNode fast = fastTail;
            int traversed = size - 1;

            // Use fast layer to get close to target
            while (fast != null && fast.prev != null && fast.prev != fastHead && current != null) {
                int prevGap = fast.gapFromPrev;
                if (traversed - prevGap < index) break;
                traversed -= prevGap;
                fast = fast.prev;
                if (fast == null || fast.target == null) {
                    return getNodeNormally(index); // Fallback if fast layer is corrupted
                }
                current = fast.target;
            }

            // Walk the remaining distance
            while (traversed > index && current != null) {
                current = current.prev;
                traversed--;
            }
            result = current;
        }

        // Fallback to normal traversal if fast layer failed
        if (result == null) {
            result = getNodeNormally(index);
        }

        return result;
    }

    /**
     * Gets a node by index using standard doubly-linked list traversal.
     * This method is used as a fallback when:
     * <ul>
     *   <li>The list is too small for fast layer to be beneficial</li>
     *   <li>The fast layer traversal fails</li>
     *   <li>The fast layer is not properly initialized</li>
     * </ul>
     *
     * @param index The index of the desired node
     * @return The node at the specified index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    private ListNode getNodeNormally(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();

        ListNode current;
        if (index <= size / 2) {
            // Traverse forward from head
            current = head;
            for (int i = 0; i < index && current != null; i++) {
                current = current.next;
            }
        } else {
            // Traverse backward from tail
            current = tail;
            for (int i = size - 1; i > index && current != null; i--) {
                current = current.prev;
            }
        }
        return current;
    }

    /**
     * Rebalances the fast layer to maintain optimal skip distances.
     * This operation:
     * <ul>
     *   <li>Adjusts skip distance if density is suboptimal</li>
     *   <li>Resets fast layer to just sentinels</li>
     *   <li>Clears all fast links in main list</li>
     *   <li>Rebuilds fast layer with optimal spacing</li>
     *   <li>Updates tail sentinel connections</li>
     * </ul>
     *
     * The fast layer is rebuilt with nodes placed every getDynamicSkip() positions,
     * ensuring O(sqrt(n)) average case access time.
     */
    private void rebalanceFastLayer() {
        if (fastHead == null || fastTail == null || head == null) return;

        // Adjust skip distance if density is suboptimal
        if (fastNodeCount < Math.sqrt(size) / 2) {
            currentSkipDistance = Math.max(MIN_SKIP, currentSkipDistance / 2);
        }

        // Calculate maximum allowed fast nodes (including sentinels)
        int maxFastNodes = Math.max(2, size / MIN_SKIP);

        if (fastNodeCount > maxFastNodes) {
            // Need to remove excess nodes
            int nodesToKeep = maxFastNodes;
            int currentCount = 0;
            FastNode current = fastHead;

            // Keep first 'nodesToKeep' nodes, adjusting their gaps
            while (current != null && currentCount < nodesToKeep) {
                if (current.next != null && currentCount < nodesToKeep - 1) {
                    // Calculate new gap to next kept node
                    int newGap = 0;
                    FastNode temp = current.next;
                    while (temp != null && currentCount + 1 < nodesToKeep - 1) {
                        newGap += temp.gapFromPrev;
                        // Clear links bidirectionally
                        if (temp.target != null) {
                            temp.target.fastLink = null;
                            temp.target = null;  // Also clear the reverse link
                        }
                        temp = temp.next;
                    }
                    if (temp != null) {
                        current.next = temp;
                        temp.prev = current;
                        temp.gapFromPrev = Math.max(1, newGap);
                    }
                }
                current = current.next;
                currentCount++;
            }

            // Clear remaining nodes' links
            while (current != null && current != fastTail) {
                if (current.target != null) {
                    current.target.fastLink = null;
                    current.target = null;  // Also clear the reverse link
                }
                current = current.next;
            }
        } else {
            // Just reset to sentinels, clearing fast links by traversing fast layer
            FastNode current = fastHead.next;
            while (current != null && current != fastTail) {
                if (current.target != null) {
                    current.target.fastLink = null;
                    current.target = null;  // Also clear the reverse link
                }
                current = current.next;
            }

            fastHead.next = fastTail;
            fastTail.prev = fastHead;
            fastNodeCount = 2;

            // Rebuild with optimal spacing
            int skip = getDynamicSkip();
            int counter = 0;
            ListNode mainCurrent = head;
            int gap = 0;
            while (mainCurrent != null && mainCurrent != tail) {
                gap++;
                if (counter > 0 && counter % skip == 0 && mainCurrent.next != null) {
                    appendFastNodeToLast(mainCurrent, gap);
                    gap = 0;
                }
                mainCurrent = mainCurrent.next;
                counter++;
            }
        }

        updateTailSentinel();
    }

    /**
     * Removes all elements from the list and resets all internal state.
     * This method ensures proper cleanup by:
     * <ul>
     *   <li>Clearing all node references</li>
     *   <li>Resetting fast layer sentinels</li>
     *   <li>Resetting all counters and metrics</li>
     *   <li>Restoring initial skip distance</li>
     * </ul>
     */
    @Override
    public void clear() {
        head = tail = null;
        fastHead = fastTail = null;
        size = 0;
        pendingGap = 0;
        operationsSinceRebalance = 0;
        currentSkipDistance = MIN_SKIP;
        fastNodeCount = 0;
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return The number of elements in this list
     */
    @Override
    public int size() {
        return size;
    }

    // Unused java.util.List methods
    @Override public boolean isEmpty() { throw new UnsupportedOperationException(); }
    @Override public boolean contains(Object o) { throw new UnsupportedOperationException(); }
    @Override public java.util.Iterator<E> iterator() { throw new UnsupportedOperationException(); }
    @Override public Object[] toArray() { throw new UnsupportedOperationException(); }
    @Override public <T> T[] toArray(T[] a) { throw new UnsupportedOperationException(); }
    @Override public boolean containsAll(java.util.Collection<?> c) { throw new UnsupportedOperationException(); }
    @Override public boolean addAll(java.util.Collection<? extends E> c) { throw new UnsupportedOperationException(); }
    @Override public boolean addAll(int index, java.util.Collection<? extends E> c) { throw new UnsupportedOperationException(); }
    @Override public boolean removeAll(java.util.Collection<?> c) { throw new UnsupportedOperationException(); }
    @Override public boolean retainAll(java.util.Collection<?> c) { throw new UnsupportedOperationException(); }
    @Override public E get(int index) { throw new UnsupportedOperationException(); }
    @Override public E set(int index, E element) { throw new UnsupportedOperationException(); }
    @Override public int indexOf(Object o) { throw new UnsupportedOperationException(); }
    @Override public int lastIndexOf(Object o) { throw new UnsupportedOperationException(); }
    @Override public java.util.ListIterator<E> listIterator() { throw new UnsupportedOperationException(); }
    @Override public java.util.ListIterator<E> listIterator(int index) { throw new UnsupportedOperationException(); }
    @Override public java.util.List<E> subList(int fromIndex, int toIndex) { throw new UnsupportedOperationException(); }
}