class SkipList:
    """Complete Python conversion of the Java MySkipList with all features."""

    class ListNode:
        __slots__ = ("data", "prev", "next", "fast")

        def __init__(self, data):
            self.data = data
            self.prev = None
            self.next = None
            self.fast = None

    class FastNode:
        __slots__ = ("target", "prev", "next", "gap")

        def __init__(self, target=None, prev=None, next=None, gap=0):
            self.target = target
            self.prev = prev
            self.next = next
            self.gap = gap

    MIN_SKIP = 25
    REBALANCE_THRESHOLD = 100
    SKIP_GROWTH_FACTOR = 1.5

    def __init__(self):
        self.head = None
        self.tail = None
        self.size = 0

        self.fast_head = None
        self.fast_tail = None
        self.fast_count = 0

        self.pending_gap = 0
        self.current_skip = self.MIN_SKIP
        self.ops_since_rebalance = 0

    # -------------------------------
    # Core helpers
    # -------------------------------

    def _get_dynamic_skip(self):
        """Calculate optimal skip distance based on current size."""
        if self.size <= 1:
            return self.MIN_SKIP

        if self.size > self.current_skip * self.SKIP_GROWTH_FACTOR:
            potential = min(
                int(self.current_skip * self.SKIP_GROWTH_FACTOR),
                int(self.size ** 0.5)
            )
            self.current_skip = potential

        return max(self.MIN_SKIP, self.current_skip)

    def _init_fast_layer(self):
        """Initialize fast layer sentinels."""
        if self.head is None or self.fast_head is not None:
            return

        try:
            self.fast_head = self.FastNode(self.head, gap=0)
            self.head.fast = self.fast_head

            if self.head == self.tail:
                self.fast_tail = self.FastNode(self.tail, self.fast_head, None, 0)
            else:
                self.fast_tail = self.FastNode(self.tail, self.fast_head, None, max(1, self.size - 1))

            self._update_tail_fast()
            self.fast_head.next = self.fast_tail
            self.fast_tail.prev = self.fast_head
            self.fast_count = 2

            # Verify initialization
            if self.fast_head.target is None or self.fast_tail.target is None:
                self._clear_fast_layer()
        except:
            self._clear_fast_layer()

    def _clear_fast_layer(self):
        """Clear the fast layer completely."""
        self.fast_head = None
        self.fast_tail = None
        self.fast_count = 0
        if self.head:
            self.head.fast = None
        if self.tail:
            self.tail.fast = None

    def _update_tail_fast(self):
        """Update fast tail sentinel to point to current tail."""
        if self.fast_tail:
            self.fast_tail.target = self.tail
            if self.tail:
                self.tail.fast = self.fast_tail

    def _append_fast(self, node, gap):
        """Append a new fast node before tail sentinel."""
        if self.fast_tail is None or self.fast_tail.prev is None or node is None:
            return

        fn = self.FastNode(node, self.fast_tail.prev, self.fast_tail, gap)
        self.fast_tail.prev.next = fn
        self.fast_tail.prev = fn
        node.fast = fn
        self.fast_count += 1

    def _remove_fast(self, fn):
        """Remove a fast node and update gaps."""
        if fn is None or fn is self.fast_head or fn is self.fast_tail:
            return

        # Update gap - merge with previous
        if fn.prev and fn.next:
            fn.next.gap = max(1, fn.next.gap + fn.gap)

        # Update fast layer links
        if fn.prev:
            fn.prev.next = fn.next
        if fn.next:
            fn.next.prev = fn.prev

        # Clear main list node's fast link
        if fn.target:
            fn.target.fast = None

        # Clear removed node's references
        fn.prev = fn.next = fn.target = None

        if self.fast_count > 2:
            self.fast_count -= 1

    def _check_and_rebalance(self):
        """Check if rebalancing is needed and perform it."""
        self.ops_since_rebalance += 1
        if (self.ops_since_rebalance >= self.REBALANCE_THRESHOLD or
                (self.fast_count > 2 and self.fast_count < (self.size ** 0.5) / 2)):
            self._rebalance()
            self.ops_since_rebalance = 0

    # -------------------------------
    # Core operations
    # -------------------------------

    def add(self, value):
        """Append element to end of list - O(1) amortized."""
        node = self.ListNode(value)

        if self.head is None:
            self.head = self.tail = node
            self.size = 1
            self._init_fast_layer()
            self.pending_gap = 0
            return

        # Append to tail
        node.prev = self.tail
        self.tail.next = node
        self.tail = node
        self.size += 1

        self._update_tail_fast()
        self.pending_gap += 1

        # Check if we need a new fast node before tail
        if self.pending_gap >= self._get_dynamic_skip():
            before_tail = self.tail.prev
            self._append_fast(before_tail, self.pending_gap - 1)
            self.fast_tail.gap = 1
            self.pending_gap = 1
        else:
            self.fast_tail.gap = self.pending_gap

    def insert(self, index, value):
        """Insert element at specified position - O(sqrt(n)) average."""
        if index < 0 or index > self.size:
            raise IndexError("Index out of bounds")

        # Use optimized add for append
        if index == self.size:
            self.add(value)
            return

        # Insert at head
        if index == 0:
            node = self.ListNode(value)
            node.next = self.head
            if self.head:
                self.head.prev = node
            else:
                self.tail = node
            self.head = node
            self.size += 1

            if self.size == 1:
                self._init_fast_layer()
            elif self.fast_head:
                self.fast_head.target = self.head
                self.head.fast = self.fast_head
                if self.fast_head.next:
                    self.fast_head.next.gap = max(1, self.fast_head.next.gap + 1)
            return

        # Find the fast node that needs gap update
        fast = self.fast_head
        update_fast = None
        traversed = 0

        while fast and fast.next:
            if traversed + fast.next.gap > index:
                update_fast = fast.next
                break
            traversed += fast.next.gap
            fast = fast.next

        # Insert at position
        curr = self.get_node(index)
        if curr is None:
            raise ValueError("Target node not found")

        node = self.ListNode(value)
        node.prev = curr.prev
        node.next = curr
        if curr.prev:
            curr.prev.next = node
        curr.prev = node
        self.size += 1

        # Update gap for affected fast node
        if update_fast:
            update_fast.gap = max(1, update_fast.gap + 1)

        # Consider rebalancing for internal insertions
        if index > 1 and index < self.size - 1:
            if (index + 1) % self._get_dynamic_skip() == 0:
                self._check_and_rebalance()

    def remove_at(self, index):
        """Remove element at specified position - O(sqrt(n)) average."""
        if index < 0 or index >= self.size:
            raise IndexError("Index out of bounds")

        # Remove head
        if index == 0:
            old_head = self.head
            data = old_head.data

            self.head = old_head.next
            if self.head:
                self.head.prev = None
            else:
                self.tail = None

            self.size -= 1
            if self.size == 0:
                self._clear_fast_layer()
                self.pending_gap = 0
                self.current_skip = self.MIN_SKIP
            else:
                self.fast_head.target = self.head
                self.head.fast = self.fast_head
                if self.fast_head.next:
                    self.fast_head.next.gap = max(1, self.fast_head.next.gap - 1)

            return data

        # Remove tail
        if index == self.size - 1:
            old_tail = self.tail
            data = old_tail.data

            if self.tail.prev:
                self.tail = old_tail.prev
                self.tail.next = None
                self.size -= 1

                self._update_tail_fast()
                if self.fast_tail and self.fast_tail.prev:
                    self.fast_tail.gap = max(1, self.pending_gap - 1)
                self.pending_gap = max(0, self.pending_gap - 1)
            else:
                self.head = self.tail = None
                self.size = 0
                self._clear_fast_layer()
                self.pending_gap = 0
                self.current_skip = self.MIN_SKIP

            return data

        # Find the fast node that needs gap update
        fast = self.fast_head
        update_fast = None
        traversed = 0

        while fast and fast.next:
            if traversed + fast.next.gap > index:
                update_fast = fast.next
                break
            traversed += fast.next.gap
            fast = fast.next

        # Remove internal node
        target = self.get_node(index)
        if target is None:
            raise ValueError("Node not found")
        data = target.data

        # Update main list connections
        if target.prev:
            target.prev.next = target.next
        if target.next:
            target.next.prev = target.prev

        self.size -= 1

        # Update fast layer
        if target.fast and target.fast is not self.fast_head and target.fast is not self.fast_tail:
            fast_node = target.fast
            if fast_node.next:
                fast_node.next.gap += fast_node.gap - 1
            self._remove_fast(fast_node)
        elif update_fast:
            update_fast.gap = max(1, update_fast.gap - 1)

        # Rebalance for internal nodes
        if index > 1 and index < self.size - 1:
            self._check_and_rebalance()

        return data

    def remove(self, value):
        """Remove first occurrence of value - optimized with chunk search."""
        if self.head is None or value is None:
            return False

        # Quick check head/tail
        if self.head.data == value:
            self.remove_at(0)
            return True
        if self.tail.data == value:
            self.remove_at(self.size - 1)
            return True

        # Parallel search from both ends
        front_fast = self.fast_head
        back_fast = self.fast_tail
        front_idx = 0
        back_idx = self.size - 1

        while (front_fast and back_fast and
               front_fast.next and back_fast.prev and
               front_idx < back_idx):

            # Check front chunk
            if self._search_chunk(front_fast, value):
                return True
            front_idx += front_fast.next.gap
            front_fast = front_fast.next

            # Check back chunk
            if front_idx < back_idx:
                if self._search_chunk(back_fast.prev, value):
                    return True
                back_idx -= back_fast.gap
                back_fast = back_fast.prev

        return False

    def _search_chunk(self, fast, value):
        """Search a chunk of the list for value."""
        if fast is None or fast.next is None:
            return False

        # Check fast node's target
        if fast.target and fast.target.data == value:
            self._remove_node_and_update(fast.target, fast)
            return True

        # Search regular nodes in chunk
        current = fast.target.next if fast.target else None
        search_end = fast.next.target
        if current is None or search_end is None:
            return False

        remaining = fast.next.gap - 1
        while remaining > 0 and current != search_end:
            if current.data == value:
                self._remove_node_and_update(current, fast)
                return True
            current = current.next
            remaining -= 1

        return False

    def _remove_node_and_update(self, node, nearest_fast):
        """Remove a node and update fast layer."""
        # Update main list
        if node.prev:
            node.prev.next = node.next
        if node.next:
            node.next.prev = node.prev

        # Update fast layer
        if node.fast:
            self._remove_fast(node.fast)
        elif nearest_fast.next:
            nearest_fast.next.gap = max(1, nearest_fast.next.gap - 1)

        self.size -= 1
        self._check_and_rebalance()

    def get_node(self, index):
        """Get node at index using fast layer - O(sqrt(n)) average."""
        if index < 0 or index >= self.size:
            raise IndexError("Index out of bounds")

        # Direct access for endpoints
        if index == 0:
            return self.head
        if index == self.size - 1:
            return self.tail

        # For small lists, use normal traversal
        if self.fast_head is None or self.fast_count <= 2 or self.size < self._get_dynamic_skip():
            return self._get_node_normal(index)

        # Use fast layer
        if index <= self.size // 2:
            # Forward from head
            cur = self.head
            fast = self.fast_head
            walked = 0

            while fast and fast.next and fast.next != self.fast_tail:
                if walked + fast.next.gap > index:
                    break
                walked += fast.next.gap
                fast = fast.next
                if not fast or not fast.target:
                    return self._get_node_normal(index)
                cur = fast.target

            while walked < index and cur:
                cur = cur.next
                walked += 1
            return cur
        else:
            # Backward from tail
            cur = self.tail
            fast = self.fast_tail
            walked = self.size - 1

            while fast and fast.prev and fast.prev != self.fast_head:
                if walked - fast.gap < index:
                    break
                walked -= fast.gap
                fast = fast.prev
                if not fast or not fast.target:
                    return self._get_node_normal(index)
                cur = fast.target

            while walked > index and cur:
                cur = cur.prev
                walked -= 1
            return cur

    def _get_node_normal(self, index):
        """Fallback: normal doubly-linked list traversal."""
        if index < 0 or index >= self.size:
            raise IndexError("Index out of bounds")

        if index <= self.size // 2:
            # Forward from head
            cur = self.head
            for _ in range(index):
                if cur is None:
                    break
                cur = cur.next
            return cur
        else:
            # Backward from tail
            cur = self.tail
            for _ in range(self.size - 1 - index):
                if cur is None:
                    break
                cur = cur.prev
            return cur

    def _rebalance(self):
        """Rebuild fast layer with optimal spacing."""
        if self.fast_head is None or self.fast_tail is None or self.head is None:
            return

        # Adjust skip distance if density is suboptimal
        if self.fast_count < (self.size ** 0.5) / 2:
            self.current_skip = max(self.MIN_SKIP, self.current_skip // 2)

        # Clear existing fast nodes (except sentinels)
        current = self.fast_head.next
        while current and current != self.fast_tail:
            if current.target:
                current.target.fast = None
            current = current.next

        # Reset to just sentinels
        self.fast_head.next = self.fast_tail
        self.fast_tail.prev = self.fast_head
        self.fast_count = 2

        # Rebuild with optimal spacing
        skip = self._get_dynamic_skip()
        counter = 0
        main_cur = self.head
        gap = 0

        while main_cur and main_cur != self.tail:
            gap += 1
            if counter > 0 and counter % skip == 0 and main_cur.next:
                self._append_fast(main_cur, gap)
                gap = 0
            main_cur = main_cur.next
            counter += 1

        self._update_tail_fast()

    # -------------------------------
    # Public API
    # -------------------------------

    def __len__(self):
        return self.size

    def get(self, index):
        """Get element at index."""
        return self.get_node(index).data

    def clear(self):
        """Remove all elements."""
        self.head = self.tail = None
        self._clear_fast_layer()
        self.size = 0
        self.pending_gap = 0
        self.ops_since_rebalance = 0
        self.current_skip = self.MIN_SKIP


# Demo usage
if __name__ == "__main__":
    lst = SkipList()

    # Test basic operations
    for i in range(100):
        lst.add(i)

    print(f"Size: {len(lst)}")
    print(f"First: {lst.get(0)}, Last: {lst.get(99)}")
    print(f"Middle: {lst.get(50)}")

    # Test insertion
    lst.insert(50, 999)
    print(f"After insert at 50: {lst.get(50)}")

    # Test removal
    lst.remove_at(50)
    print(f"After remove at 50: {lst.get(50)}")

    # Test remove by value
    result = lst.remove(42)
    print(f"Removed 42: {result}")