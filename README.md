# SkipList: Optimized Doubly-Linked List

A custom doubly-linked list implementation with O(√n) average-case access time through a skip-list inspired fast-access layer.

Available in both **Java** and **Python** with identical functionality and performance characteristics.

## Overview

Traditional linked lists have O(n) access time, making random access operations slow. This implementation adds a sparse "fast layer" above the main linked list, enabling much faster access to elements at arbitrary positions while maintaining the benefits of linked list insertion/deletion.

## Performance Characteristics

| Operation | Complexity | Notes |
|-----------|------------|-------|
| `add(value)` | O(1) amortized | Optimized tail operations with gap tracking |
| `insert(index, value)` | O(√n) average | Uses fast layer for positioning |
| `remove(index)` | O(√n) average | Uses fast layer for positioning |
| `remove(value)` | O(n) worst case | Optimized with bidirectional chunk-based search |
| `get(index)` | O(√n) average | Bidirectional search with fast layer |

## Key Features

- **Two-Layer Architecture**: Main doubly-linked list + sparse fast-access layer
- **Gap-Based Indexing**: Fast layer uses relative gaps between nodes instead of absolute indices
- **Dynamic Skip Distance**: Adjusts spacing between fast nodes based on list size (~√n)
- **Adaptive Rebalancing**: Maintains optimal fast layer density automatically
- **Bidirectional Search**: Chooses optimal direction (forward/backward) for access operations

## Repository Structure

```
skiplist/
├── README.md           # This file
├── java/
│   ├── README.md       # Java-specific documentation
│   └── SkipList/
│       └── SkipList.java
└── python/
    ├── README.md       # Python-specific documentation
    ├── skiplist/
    │   ├── __init__.py
    │   └── skiplist.py
    └── setup.py
```

## Quick Start

### Java
```java
import SkipList.SkipList;

SkipList<Integer> list = new SkipList<>();
list.add(42);
list.add(0, 99);  // Insert at index 0
Integer value = list.remove(0);
```

See [java/README.md](java/README.md) for full documentation.

### Python
```python
from skiplist import SkipList

lst = SkipList()
lst.add(42)
lst.insert(0, 99)  # Insert at index 0
value = lst.remove_at(0)
```

See [python/README.md](python/README.md) for full documentation.

## Why This Approach?

This implementation originated from frustration with traditional linked lists' poor mid-list access performance:

- **vs. Standard Linked Lists**: Much faster random access (O(√n) vs O(n))
- **vs. Dynamic Arrays**: Better insertion/deletion in middle (O(√n) vs O(n))
- **vs. Full Skip Lists**: Simpler, more predictable, no probabilistic behavior

Perfect for scenarios requiring both frequent random access and mid-list modifications.

## Design Philosophy

This is **not** a traditional skip list (which uses probabilistic multi-level structures for O(log n) access). Instead, it uses a deterministic single-level fast layer for:

- Predictable performance (no probabilistic behavior)
- Simpler implementation and maintenance
- Lower memory overhead
- Still provides significant improvement over O(n)

The skip distance grows proportionally to √n, balancing fast layer traversal with fine-grained access.

## Origin

Originally implemented in Java to solve real-world performance issues with mid-list access patterns. Later ported to Python to explore the same algorithmic approach across different languages while preserving identical behavior.

## License

MIT License - feel free to use and modify as needed.

## Contributing

This is a personal learning project exploring data structure optimization across languages. Suggestions and improvements are welcome! Open an issue or submit a pull request.