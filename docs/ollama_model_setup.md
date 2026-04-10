# Using the Modelfile to Create a Custom LLM with Ollama

This guide explains how to create a custom LLM using the modelfile for the G.O.A.T framework.

## Prerequisites

1. Install Ollama
   - Download from [Ollama's website](https://ollama.ai/)
   - Install and ensure the service is running

2. Ensure you have the base model (llama3.1) available:
   ```
   ollama pull llama3.1
   ```

## Creating the Custom Model

### Method 1: Using the Setup Script (Recommended)

We've prepared a batch script to automate the model setup process:

1. Open Command Prompt
2. Navigate to the G.O.A.T bin directory:
   ```
   cd /d D:\git\g.o.a.t\product_package\bin
   ```
3. Run the setup script:
   ```
   setup_ollama_model.bat
   ```
4. Wait for the script to complete - it will:
   - Check if Ollama is installed
   - Create the custom model using our modelfile
   - Display the status

### Method 2: Manual Creation

If you prefer to create the model manually:

1. Open Command Prompt
2. Navigate to the directory containing the modelfile:
   ```
   cd /d D:\git\g.o.a.t\docs
   ```
3. Create the model using the modelfile:
   ```
   ollama create goat-llm -f modelfile
   ```
4. Wait for model creation to complete

## Verifying the Model Creation

To confirm the model was created successfully:

1. List all available models:
   ```
   ollama list
   ```
2. Check that `goat-llm` appears in the list

## Testing the New Model

You can test the model directly with Ollama:

```
ollama run goat-llm "Convert this test case to operations: Test case ID TC001: Verify the product.conf file exists after installation"
```

You should receive a properly structured JSON response with operations.

## Using with G.O.A.T Framework

The G.O.A.T framework is already configured to use the custom model when available:

1. The `LLAMAClient` class will automatically detect and use the `goat-llm` model if available
2. If not found, it will fall back to using the base `llama3.1` model

No changes to your workflow are needed - the system will use the optimal model automatically.

## Understanding the Model Configuration

Our custom model includes:

- **Low temperature** (0.1) for more deterministic responses
- **JSON mode** enabled for valid JSON output
- **System prompt** with specific instructions for G.O.A.T operations
- **Templates** showing the expected input/output format
- **Context window** of 4096 tokens to handle complex test cases

## Troubleshooting

If you encounter any issues:

### Model Not Found
```
Error: model 'goat-llm' not found
```
- Ensure Ollama is running (`ollama serve` if needed)
- Try creating the model again

### Creation Fails
- Check disk space
- Verify you have the base model `llama3.1` installed
- Look for syntax errors in the modelfile

### Out of Memory
- Try closing other applications
- Restart Ollama: `ollama stop` and then `ollama serve`

### Connection Issues
- Ensure Ollama is running on port 11434
- Check firewall settings

## Updating the Model

If you make changes to the modelfile:

1. Delete the existing model:
   ```
   ollama rm goat-llm
   ```
2. Recreate the model using the updated modelfile:
   ```
   ollama create goat-llm -f modelfile
   ```
