import java.util.ArrayList;

public class TxHandler {

    private UTXOPool utxoPool;
    private UTXOPool testingPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
        this.testingPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        ArrayList<Transaction.Output> outputs = tx.getOutputs();

        // track input and output totals
        double totalInput = 0;
        double totalOutput = 0;

        // get all current utxos for internal manipulation
        UTXOPool utxoPool = new UTXOPool(this.testingPool);
        for (int i = 0; i < outputs.size(); ++i) {
            Transaction.Output output = outputs.get(i);
            // (4) cannot have negative values as outputs
            if (output.value < 0) {
                return false;
            }
            // add outputs from this transaction if needed
            UTXO utxo = new UTXO(tx.getHash(), i);
            if (testingPool.contains(utxo)) {
                this.testingPool.addUTXO(utxo, outputs.get(i));
            }
            // track sum of outputs
            totalOutput += output.value;
        }

        // validate all of the inputs
        for (int i = 0; i < inputs.size(); ++i) {
            Transaction.Input input = inputs.get(i);

            // (1) verify that all input being claimed is in the current utxo pool
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (utxoPool.contains(utxo)) {
                Transaction.Output output = utxoPool.getTxOutput(utxo);
                // (2) verify that all the signatures are valid
                if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(i), input.signature)) {
                    return false;
                }
                // track sum of inputs (which is equal to the sum of the value its consuming)
                totalInput += output.value;
                // (3) remove utxo from pool so that it is not claimed twice
                utxoPool.removeUTXO(utxo);
            } else {
                return false;
            }
        }

        // (5) the sum of inputs has to be greater than or equal than that of outputs
        if (totalInput < totalOutput) {
            return false;
        }

        // update utxo pool
        this.testingPool = utxoPool;

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        this.testingPool = new UTXOPool(this.utxoPool);

        // add all transactions to testing pool
        for (Transaction transaction : possibleTxs) {
            ArrayList<Transaction.Output> outputs = transaction.getOutputs();
            for (int i = 0; i < outputs.size(); ++i) {
                 this.testingPool.addUTXO(new UTXO(transaction.getHash(), i), outputs.get(i));
            }
        }

        // only accept valid transactions
        ArrayList<Transaction> acceptedTransactions = new ArrayList<>();
        for (Transaction transaction : possibleTxs) {
            if (isValidTx(transaction)) {
                acceptedTransactions.add(transaction);
            } else {
                // remove transactions from pool
                ArrayList<Transaction.Output> outputs = transaction.getOutputs();
                for (int i = 0; i < outputs.size(); ++i) {
                    this.testingPool.removeUTXO(new UTXO(transaction.getHash(), i));
                }
            }
        }

        // transform to native array
        Transaction[] transactions = new Transaction[acceptedTransactions.size()];
        for (int i = 0; i < transactions.length; ++i) {
            transactions[i] = acceptedTransactions.get(i);
        }

        // assign new test pool to utxo pool
        this.utxoPool = this.testingPool;

        return transactions;
    }

}
