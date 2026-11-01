# Future improvements

Ideas that are out of scope for now and can be picked up when they are needed.

- **Explorer checkpointing:** a killed explorer loses its search. Checkpointing the search state to the store would let a session resume after being preempted. This becomes worth doing if long sessions run on spot capacity.
