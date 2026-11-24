#!/usr/bin/env python3
"""Independent, offline-only MS-AR walk-forward rerun.

This is not a bitwise replay of the original experiment: its source code was
not recovered. It implements the explicit model specification documented in
docs/INDEPENDENT_MULTIPERIOD_RERUN.md.
"""
import argparse, hashlib, json, math, os, time, zipfile
import numpy as np
from numba import njit
from scipy.optimize import minimize

ROOT="."
PARTS=[]
PART_HASHES=[]
ASSETS=["BTCUSDT","ETHUSDT","SOLUSDT","XRPUSDT","ADAUSDT","DOGEUSDT","SHIBUSDT","PEPEUSDT","WIFUSDT"]
FOLD_STARTS=[0,190271,380543,570815,761087,951359]

@njit
def logistic(x):
    if x>=0: return 1.0/(1.0+math.exp(-x))
    e=math.exp(x); return e/(1.0+e)

@njit
def msar_nll(t,x):
    p1,p2,c0,c1=t[0],t[1],t[2],t[3]
    s0,s1=math.exp(t[4]),math.exp(t[5])
    a,b=logistic(t[6]),logistic(t[7]); ab,ba=1-a,1-b
    den=ab+ba; q0=ba/den if den>1e-12 else .5; q1=1-q0
    ll=0.0
    for i in range(2,len(x)):
        v=p1*x[i-1]+p2*x[i-2]
        e0=(x[i]-c0-v)/s0; e1=(x[i]-c1-v)/s1
        f0=math.exp(-.5*e0*e0)/(s0*2.5066282746310002)
        f1=math.exp(-.5*e1*e1)/(s1*2.5066282746310002)
        z0=q0*a+q1*ba; z1=q0*ab+q1*b
        z=max(z0*f0+z1*f1,1e-300); ll+=math.log(z)
        q0=z0*f0/z; q1=z1*f1/z
    return -ll

@njit
def hmm_nll(t,x):
    m0,m1=t[0],t[1]; s0,s1=math.exp(t[2]),math.exp(t[3])
    a,b=logistic(t[4]),logistic(t[5]); ab,ba=1-a,1-b
    den=ab+ba; q0=ba/den if den>1e-12 else .5; q1=1-q0
    ll=0.0
    for i in range(len(x)):
        e0=(x[i]-m0)/s0; e1=(x[i]-m1)/s1
        f0=math.exp(-.5*e0*e0)/(s0*2.5066282746310002)
        f1=math.exp(-.5*e1*e1)/(s1*2.5066282746310002)
        if i:
            z0=q0*a+q1*ba; z1=q0*ab+q1*b
        else: z0,z1=q0,q1
        z=max(z0*f0+z1*f1,1e-300); ll+=math.log(z)
        q0=z0*f0/z; q1=z1*f1/z
    return -ll

@njit
def msar_scores(t,x):
    p1,p2,c0,c1=t[0],t[1],t[2],t[3]
    s0,s1=math.exp(t[4]),math.exp(t[5])
    a,b=logistic(t[6]),logistic(t[7]); ab,ba=1-a,1-b
    den=ab+ba; q0=ba/den if den>1e-12 else .5; q1=1-q0
    out=np.empty(len(x)-2)
    for k,i in enumerate(range(2,len(x))):
        v=p1*x[i-1]+p2*x[i-2]
        e0=(x[i]-c0-v)/s0; e1=(x[i]-c1-v)/s1
        f0=math.exp(-.5*e0*e0)/(s0*2.5066282746310002)
        f1=math.exp(-.5*e1*e1)/(s1*2.5066282746310002)
        z0=q0*a+q1*ba; z1=q0*ab+q1*b
        z=max(z0*f0+z1*f1,1e-300); out[k]=math.log(z)
        q0=z0*f0/z; q1=z1*f1/z
    return out

@njit
def hmm_scores(t,x):
    m0,m1=t[0],t[1]; s0,s1=math.exp(t[2]),math.exp(t[3])
    a,b=logistic(t[4]),logistic(t[5]); ab,ba=1-a,1-b
    den=ab+ba; q0=ba/den if den>1e-12 else .5; q1=1-q0
    out=np.empty(len(x))
    for i in range(len(x)):
        e0=(x[i]-m0)/s0; e1=(x[i]-m1)/s1
        f0=math.exp(-.5*e0*e0)/(s0*2.5066282746310002)
        f1=math.exp(-.5*e1*e1)/(s1*2.5066282746310002)
        if i:
            z0=q0*a+q1*ba; z1=q0*ab+q1*b
        else: z0,z1=q0,q1
        z=max(z0*f0+z1*f1,1e-300); out[i]=math.log(z)
        q0=z0*f0/z; q1=z1*f1/z
    return out

def load_returns():
    global PART_HASHES
    hashes=[]
    for p in PARTS:
        h=hashlib.sha256()
        with open(p,"rb") as f:
            for chunk in iter(lambda:f.read(8*1024*1024),b""): h.update(chunk)
        hashes.append(h.hexdigest())
        with zipfile.ZipFile(p) as z:
            m=json.loads(z.read("MANIFEST.json"))
            if m.get("monthly_archives_expected")!=m.get("monthly_archives_verified") or not all(x.get("checksum_verified") for x in m.get("files",[])):
                raise ValueError("DATA_INTEGRITY: part manifest does not verify "+p)
    PART_HASHES=hashes
    cache=ROOT+"/returns_cache.npz"; meta=ROOT+"/returns_cache_manifest.json"
    if os.path.exists(cache) and os.path.exists(meta):
        if json.load(open(meta,encoding="utf-8")).get("part_sha256")==hashes:
            z=np.load(cache); return {s:z[s] for s in ASSETS}
    out={}
    for sym in ASSETS:
        ts=[]; closes=[]
        for p in PARTS:
            with zipfile.ZipFile(p) as z:
                names=sorted(n for n in z.namelist() if n.startswith(sym+"/") and n.endswith(".normalized.csv"))
                for name in names:
                    with z.open(name) as f:
                        f.readline()
                        for line in f:
                            c=line.decode().strip().split(",")
                            ts.append(int(c[0])); closes.append(float(c[4]))
        ts=np.asarray(ts,dtype=np.int64); closes=np.asarray(closes,dtype=np.float64)
        order=np.argsort(ts); ts=ts[order]; closes=closes[order]
        if len(closes)!=963360 or np.any(np.diff(ts)!=60000):
            raise ValueError(f"{sym}: expected 963360 continuous one-minute candles")
        out[sym]=np.diff(np.log(closes))
        print("loaded",sym,len(closes),"candles",flush=True)
    np.savez_compressed(cache,**out)
    json.dump({"part_sha256":hashes,"assets":ASSETS,"rows_per_asset":963360},open(meta,"w"),indent=2)
    return out

def fit_msar(x):
    sd=np.std(x); base=np.array([0.,0.,-.05*sd,.05*sd,math.log(sd*.75),math.log(sd*1.35),4.,4.])
    bounds=[(-.95,.95),(-.95,.95),(-.01,.01),(-.01,.01),(math.log(sd*.15),math.log(sd*4)),(math.log(sd*.15),math.log(sd*4)),(-5,8),(-5,8)]
    best=None
    for swap in (False,True):
        x0=base.copy()
        if swap: x0[[2,3]]=x0[[3,2]]; x0[[4,5]]=x0[[5,4]]
        r=minimize(msar_nll,x0,args=(x,),method="L-BFGS-B",bounds=bounds,options={"maxiter":120,"ftol":1e-8})
        if best is None or r.fun<best.fun: best=r
    return best

def fit_hmm(x):
    sd=np.std(x); q=np.quantile(x,[.25,.75])
    x0=np.array([q[0],q[1],math.log(sd*.75),math.log(sd*1.25),4.,4.])
    bounds=[(-.02,.02),(-.02,.02),(math.log(sd*.15),math.log(sd*4)),(math.log(sd*.15),math.log(sd*4)),(-5,8),(-5,8)]
    return minimize(hmm_nll,x0,args=(x,),method="L-BFGS-B",bounds=bounds,options={"maxiter":120,"ftol":1e-8})

def paired_mbb(deltas,reps,seed,block=60):
    rng=np.random.default_rng(seed); n=len(deltas[0]); full=n//block; rem=n%block
    total=np.zeros(reps); denom=len(deltas)*n
    for x in deltas:
        cs=np.r_[0.,np.cumsum(x)]; starts=np.arange(n-block+1)
        sums=cs[starts+block]-cs[starts]
        chosen=rng.integers(0,len(starts),size=(reps,full+1))
        v=sums[chosen[:,:full]].sum(axis=1)
        if rem: v+=cs[chosen[:,full]+rem]-cs[chosen[:,full]]
        total+=v
    boots=total/denom; mean=sum(x.sum() for x in deltas)/denom
    p=(1+np.sum(np.abs(boots-mean)>=abs(mean)))/(reps+1)
    return mean,float(np.quantile(boots,.025)),float(np.quantile(boots,.975)),float(p)

def holm(ps):
    ps=np.asarray(ps); order=np.argsort(ps); adj=np.empty(len(ps)); run=0.
    for rank,i in enumerate(order):
        run=max(run,min(1.,(len(ps)-rank)*ps[i])); adj[i]=run
    return adj

def main():
    global ROOT,PARTS
    ap=argparse.ArgumentParser(description="Independent offline MS-AR walk-forward rerun.")
    ap.add_argument("--part",action="append",required=True,help="Inner dataset ZIP; pass exactly three times.")
    ap.add_argument("--output-dir",default="parsa-rerun-output")
    a=ap.parse_args()
    if len(a.part)!=3: raise SystemExit("Pass three --part paths in dataset-part order.")
    ROOT=os.path.abspath(a.output_dir); os.makedirs(ROOT,exist_ok=True); PARTS=[os.path.abspath(x) for x in a.part]
    t0=time.time(); data=load_returns(); rows=[]; scores={}
    for ai,sym in enumerate(ASSETS):
        cp=ROOT+"/checkpoint_"+sym+".npz"
        if os.path.exists(cp):
            z=np.load(cp); folds=[{k:z[f"{i}_{k}"] for k in ("msar","hmm","lagged_volatility","train_gaussian")} for i in range(6)]
        else:
            folds=[]; r=data[sym]
            for fi,start in enumerate(FOLD_STARTS):
                train=r[start:start+10000]; test=r[start+10000:start+12000]
                ms=fit_msar(train); hm=fit_hmm(train)
                if not ms.success or not hm.success: print("optimizer warning",sym,fi,ms.success,hm.success,flush=True)
                full=np.r_[train,test]
                sm=msar_scores(ms.x,full)[-len(test):]
                sh=hmm_scores(hm.x,full)[-len(test):]
                mu=float(np.mean(train)); sd=max(float(np.std(train)),1e-8)
                sg=-.5*np.log(2*np.pi*sd*sd)-.5*((test-mu)/sd)**2
                hist=full; sv=np.empty(len(test))
                for k in range(len(test)):
                    vol=max(float(np.std(hist[len(train)+k-60:len(train)+k])),1e-8)
                    sv[k]=-.5*np.log(2*np.pi*vol*vol)-.5*(test[k]/vol)**2
                folds.append({"msar":sm,"hmm":sh,"lagged_volatility":sv,"train_gaussian":sg})
                print("fit",sym,"fold",fi,flush=True)
            np.savez_compressed(cp,**{f"{i}_{k}":v for i,f in enumerate(folds) for k,v in f.items()})
        scores[sym]=folds
        for comp in ("hmm","lagged_volatility","train_gaussian"):
            d=[f["msar"]-f[comp] for f in folds]
            mean,lo,hi,p=paired_mbb(d,10000,20261125+ai*31+("hmm","lagged_volatility","train_gaussian").index(comp))
            rows.append({"symbol":sym,"comparison":"MSAR-"+comp,"n":sum(len(x) for x in d),"mean_delta":mean,"ci95_low":lo,"ci95_high":hi,"p_raw":p})
        print("asset complete",sym,flush=True)
    adj=holm([r["p_raw"] for r in rows])
    for r,p in zip(rows,adj): r["p_holm"]=float(p); r["reject_0_05_after_holm"]=bool(p<.05)
    np.savez_compressed(ROOT+"/oos_paired_scores.npz",**{f"{s}_{i}_{k}":v for s,fs in scores.items() for i,f in enumerate(fs) for k,v in f.items()})
    out={"status":"INDEPENDENT_REPRODUCTION_NOT_BITWISE_REPLAY","data_period":"2025-01 through 2026-10","assets":ASSETS,"rows_per_asset":963360,"total_source_rows":8670240,"monthly_archives_verified":198,"part_sha256":PART_HASHES,"folds":6,"train_size":10000,"test_size":2000,"oos_observations":108000,"models":"2-state Gaussian MS-AR(2), 2-state Gaussian HMM, training Gaussian, 60-return lagged-volatility Gaussian","bootstrap":"10,000 non-circular moving blocks of 60 observations within each fold; percentile 95% CI; centered two-sided p; Holm across 27 tests","results":rows,"rejected_after_holm":sum(r["reject_0_05_after_holm"] for r in rows),"limitations":["Original experiment source code was not present; this is an independent implementation, not a bitwise replay. Original per-asset input hashes do not match reconstructed normalized CSV byte streams.","Forecast log-score differences are not trading PnL or evidence of net tradable alpha.","No fees, spread, slippage, funding, latency, execution or paper/live trading are tested."]}
    json.dump(out,open(ROOT+"/independent_rerun_results.json","w"),indent=2)
    print(json.dumps({"elapsed_sec":time.time()-t0,"rejected_after_holm":out["rejected_after_holm"]},indent=2))

if __name__=="__main__": main()
