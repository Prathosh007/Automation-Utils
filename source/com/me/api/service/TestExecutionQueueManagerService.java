package com.me.api.service;

import com.google.gson.JsonObject;
import com.me.api.model.TestCaseQueueData;
import com.me.util.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class TestExecutionQueueManagerService {
    private static final Logger LOGGER = LogManager.getLogger(TestExecutionQueueManagerService.class, LogManager.LOG_TYPE.FW);
    private final Queue<TestCaseQueueData> testExecutionQueue = new ConcurrentLinkedQueue<TestCaseQueueData>();

    public static final String TEST_INITIATED_MESSAGE = "Test Initiated Successfully";
    public static final String TEST_IN_QUEUE_MESSAGE = "Previous /Another Testing Is In Progress , Hence Waiting In Queue";
    public String currentTest = null;
    private boolean processorInProgress = false;

    @Autowired
    private TestExecutionService testExecutionService;

    private TestExecutionQueueManagerService() {
    }

    public String addTestInQueue(TestCaseQueueData testCaseQueueData) {
        String response = TEST_IN_QUEUE_MESSAGE;
        if (isTestQueueEmpty() && currentTest == null) {
            response = TEST_INITIATED_MESSAGE;
        }
        testExecutionQueue.add(testCaseQueueData);
        if (!processorInProgress) {
            initiateTestFromQueue();
        }
        return response;
    }

    public boolean isTestQueueEmpty() {
        return testExecutionQueue.isEmpty();
    }

    private void initiateTestFromQueue() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                processorInProgress = true;
                while (!isTestQueueEmpty()) {
                    try {
                        if (currentTest == null) {
                            wait(5 * 1000);
                            TestCaseQueueData testCaseQueueData = testExecutionQueue.poll();
                            LOGGER.info("Executing Test :: " + testCaseQueueData.getTestUniqueId());
                            try {
                                currentTest = testCaseQueueData.getTestUniqueId();
                                LOGGER.log(Level.INFO, "Executing test case in separate thread");
                                if (testCaseQueueData.getInitiatedType().equals(TestCaseQueueData.INITIATED_TYPE.EXECUTE_WITH_JSON)) {
                                    JsonObject result = testExecutionService.executeTestCaseFromJson(testCaseQueueData.getTestUniqueId(), testCaseQueueData.getTestCaseJson());
                                } else {
                                    JsonObject result = testExecutionService.executeTestCase(testCaseQueueData.getTestUniqueId(), testCaseQueueData.getTestCaseId());
                                }
                                LOGGER.log(Level.INFO, "Test execution completed successfully in thread");
                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "Error during test execution in thread", e);
                                throw new CompletionException(e);
                            }
                            currentTest = null;
                        }

                        wait(2 * 1000);
                    } catch (Exception e) {
                        LOGGER.warning("Exception While Executing Test Case :: " + e);
                    }

                }
                processorInProgress = false;
            }
        }).start();
    }
}
