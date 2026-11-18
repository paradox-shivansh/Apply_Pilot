# ApplyPilot AI

## Step 1: Job reader (this folder)
Reads job openings from public job-board APIs, filters them cheaply, and uses an LLM
(via LangChain + LangGraph) to turn each posting into structured data.

    pip install -r requirements.txt langchain-anthropic    # or langchain-openai
    cp .env.example .env   # add your key, then: export $(cat .env | xargs)
    python -m applypilot.cli --greenhouse stripe --keywords python,backend --max-jobs 5
    pytest                 # offline tests, no key needed

## Step 2 (next): Resume coach
Parses your resume, compares it with the stored jobs, and suggests grounded improvements.
