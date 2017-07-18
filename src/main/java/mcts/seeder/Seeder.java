package mcts.seeder;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import mcts.utils.Priority;
import mcts.utils.PriorityRunnable;

/**
 * Abstract class for the runnable that executes the seeding code.
 * By default the seeding cods has a higher priority over the search
 * or it would never be executed. 
 * @author sorinMD
 *
 */
@JsonTypeInfo(use = Id.CLASS,
include = JsonTypeInfo.As.PROPERTY,
property = "type")
@JsonSubTypes({
})
public abstract class Seeder implements Runnable, PriorityRunnable{
	private static Priority priority = Priority.HIGH;
	
	@Override
	public void run() { }

	@Override
	public int getPriority() {
		return priority.getValue();
	}

}
